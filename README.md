# oauth_signup_token

Spring Boot용 소셜로그인 온보딩 signup token 라이브러리.
OAuth 콜백에서 토큰을 발급하고 (JWT + Redis + HttpOnly 쿠키) 사용자가 온보딩 도중 이탈해도 다시 이어서 회원가입할 수 있게 해줍니다.

- **JWT** 서명/검증/파싱 (JJWT 0.12.6)
- **Redis 이중 검증** — 서명 + 저장소 대조로 서버가 강제 무효화 가능
- **HttpOnly · SameSite=None · Secure** 기본 쿠키 헬퍼 (HTTPS/크로스도메인 대응)
- **온보딩 필드 자유 추가** — 하드코딩 없음, fluent `.claim(k,v)` 로 아무 값 추가
- **TTL per-token 오버라이드** 지원
- **SecurityConfig CORS 헬퍼** 제공 (allowCredentials=true 자동)
- **Spring Boot AutoConfiguration** — 빈 자동 등록

---

## 1. 설치 (GitHub Packages)

이 라이브러리는 **GitHub Packages** 에 배포됩니다. Private repo 도 무료로 사용 가능하며 PAT(Personal Access Token)만 있으면 됩니다.

### 1-1. Consumer 세팅 (라이브러리 사용하는 프로젝트)

**Step 1. GitHub PAT 발급**
[Settings → Developer settings → Personal access tokens (classic)](https://github.com/settings/tokens/new)
- Note: `read:packages for oauth_signup_token`
- Scope 체크: **`read:packages`** (필수), **`repo`** (private repo인 경우)
- 생성 후 토큰(ghp_xxx…) 복사

**Step 2. 로컬 gradle.properties 등록** (커밋 금지)
`~/.gradle/gradle.properties`
```properties
gpr.user=catomat0
gpr.token=ghp_xxxxxxxxxxxxxxxxxxxxx
```

**Step 3. 프로젝트 `build.gradle`**
```gradle
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/catomat0/oauth_signup_token')
        credentials {
            username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = project.findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'com.github.catomat0:oauth_signup_token:1.0.0'
}
```

### 1-2. Publisher 세팅 (라이브러리 배포자)

**자동 배포 (권장)** — GitHub Release 생성 시 Actions 워크플로우가 자동 publish
```
1. build.gradle 코드 확정 후 git push
2. GitHub → Releases → "Draft a new release"
3. Tag: v1.0.0 (앞에 v 붙임) → Publish release
4. .github/workflows/publish.yml 이 자동 실행 → v prefix 제거 후 1.0.0 으로 배포
```

**수동 배포** (로컬에서 직접)
```bash
export GITHUB_ACTOR=catomat0
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxx    # write:packages 권한 필요
export RELEASE_VERSION=1.0.0
./gradlew publish
```

**GitHub UI에서 원클릭 배포** (커밋 안 하고 배포만)
```
Actions → "Publish to GitHub Packages" → Run workflow → version 입력
```

### 1-3. 대안: 로컬 개발용 mavenLocal
여러 프로젝트에서 실험만 할 때는 GitHub 거치지 않고:
```bash
./gradlew publishToMavenLocal      # 라이브러리 쪽
```
```gradle
repositories { mavenLocal() }      # 사용하는 프로젝트 쪽
dependencies { implementation 'com.github.catomat0:oauth_signup_token:1.0.0-SNAPSHOT' }
```

**필수 런타임 의존성** (이식받는 프로젝트에 이미 있어야 함):
- `spring-boot-starter-web`
- `spring-boot-starter-data-redis`
- Redis 서버 **6.2 이상** (`validateAndConsume` 이 사용하는 `GETDEL` 명령 필요)

---

## 2. 설정 (`application.yml`)

```yaml
signup-token:
  secret-key: ${SIGNUP_TOKEN_SECRET}      # 필수 (32byte 이상 권장)
  expiration: 1800000                     # ms 단위, 기본 30분
  redis-key-prefix: "ST:"                 # Redis key prefix, 기본값
  cookie:
    name: signup_token                    # 기본값
    path: /                               # 기본값
    domain: example.com                   # 선택 (프로덕션 도메인)
    http-only: true                       # 기본값
    secure: true                          # 기본값 (SameSite=None 시 필수)
    same-site: None                       # 기본값
```

환경변수 예시:
```bash
export SIGNUP_TOKEN_SECRET="your-secret-key-at-least-32-bytes-long-please"
```

> ⚠️ **HS256 알고리즘은 secret이 최소 32byte(256bit) 필요.** 짧으면 `WeakKeyException` 발생.
> 안전한 secret 생성:
> ```bash
> openssl rand -base64 48
> ```

---

## 3. 사용 시나리오

### 3-1. OAuth 콜백 → 토큰 발급 + 쿠키 세팅
```java
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final SignupTokenProvider signupTokenProvider;
    private final SignupTokenService signupTokenService;
    private final SignupTokenCookieWriter cookieWriter;
    private final OAuthClient oauthClient;
    private final UserRepository userRepository;

    @GetMapping("/api/auth/oauth2/callback")
    public ResponseEntity<?> callback(@RequestParam String code,
                                     HttpServletResponse response) {
        OAuthUserInfo info = oauthClient.getUserInfo(code);

        if (userRepository.existsByProviderAndProviderId(info.provider(), info.providerId())) {
            // 기존 회원 → 로그인 처리
            return ResponseEntity.ok().build();
        }

        // 신규 → signup token 발급 (온보딩에서 받을 값을 여기서 자유롭게 담기)
        String token = signupTokenProvider.builder(info.provider(), info.providerId(), info.email())
                .claim("nickname", info.nickname())
                .claim("profileImage", info.profileImage())
                .build();

        signupTokenService.save(info.provider(), info.providerId(), token);
        cookieWriter.write(response, token);

        return ResponseEntity.ok(Map.of("needsSignup", true));
    }
}
```

### 3-2. 온보딩 스텝 진행 중 추가 값 재발급
스텝별로 값이 늘어나면 토큰을 재발급해서 계속 쿠키에 유지:
```java
@PostMapping("/api/auth/onboarding/step1")
public void step1(@RequestBody Step1Request req,
                 HttpServletRequest request,
                 HttpServletResponse response) {
    String oldToken = cookieWriter.read(request);
    SignupTokenPayload p = signupTokenProvider.parse(oldToken);

    String newToken = signupTokenProvider.builder(p.provider(), p.providerId(), p.email())
            .claims(p.extras())                      // 기존 값 유지
            .claim("nickname", req.nickname())       // 신규 값 추가
            .claim("birthYear", req.birthYear())
            .build();

    signupTokenService.save(p.provider(), p.providerId(), newToken);
    cookieWriter.write(response, newToken);
}
```

### 3-3. 회원가입 완료 (검증 → DB 저장 → 토큰 원자적 소비)
`validateAndConsume` 은 Redis `GETDEL` 로 조회+삭제를 원자화. 동시 요청 시 중복 회원가입 방지.

```java
@PostMapping("/api/auth/signup")
public ResponseEntity<?> signup(@RequestBody SignupRequest req,
                               HttpServletRequest request,
                               HttpServletResponse response) {
    String token = cookieWriter.read(request);
    if (token == null || !signupTokenProvider.validate(token)) {
        throw new IllegalStateException("Invalid signup token");
    }

    SignupTokenPayload payload = signupTokenProvider.parse(token);

    // 원자적 소비: 이 시점 이후 같은 토큰으로 재호출 불가
    if (!signupTokenService.validateAndConsume(payload.provider(), payload.providerId(), token)) {
        throw new IllegalStateException("Signup token expired or already used");
    }

    userRepository.save(User.of(
            payload.provider(),
            payload.providerId(),
            payload.email(),
            payload.extra("nickname"),
            payload.extra("birthYear"),
            req.termAgreementIds()
    ));

    cookieWriter.clear(response);
    return ResponseEntity.ok().build();
}
```

> `validate()` 는 조회만 하고 삭제하지 않음. 온보딩 스텝 중간처럼 여러 번 참조가 필요할 때 사용.
> 회원가입 완료처럼 **1회성 보장이 필요한 지점에서는 반드시 `validateAndConsume()` 사용.**

### 3-4. TTL per-token 오버라이드
```java
String token = signupTokenProvider.builder("kakao", "12345", "test@test.com")
        .claim("nickname", "홍길동")
        .ttl(Duration.ofHours(1))     // 이 토큰만 1시간
        .build();
```

### 3-5. 쿠키 옵션 per-call 오버라이드
```java
// 특정 API만 SameSite=Lax + 1시간짜리 쿠키
cookieWriter.write(response, token,
        new CookieOptions().sameSite("Lax").maxAge(Duration.ofHours(1)));
```

---

## 4. SecurityConfig 통합

`SameSite=None` 쿠키를 크로스도메인에서 쓰려면 CORS `allowCredentials(true)` 가 필수입니다.
`SignupTokenSecurity` 헬퍼로 한 줄 세팅:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(
                    SignupTokenSecurity.corsForCookieAuth("https://*.example.com")
            ))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/api/auth/oauth2/**",
                            "/api/auth/onboarding/**",
                            "/api/auth/signup"
                    ).permitAll()
                    .anyRequest().authenticated());
        return http.build();
    }
}
```

**여러 origin 리스트로 지정:**
```java
SignupTokenSecurity.corsForCookieAuth(List.of(
    "https://web.example.com",
    "https://admin.example.com"
));
```

**특정 path 에만 CORS 적용:**
```java
SignupTokenSecurity.corsForCookieAuth(List.of("https://*.example.com"), "/api/**");
```

**기존 CORS 설정과 병합** (raw config 받아서 커스텀 추가):
```java
CorsConfiguration cfg = SignupTokenSecurity.cookieAuthCorsConfig(List.of("https://*.example.com"));
cfg.addAllowedHeader("X-Custom-Header");
// UrlBasedCorsConfigurationSource 에 직접 등록
```

---

## 5. API 요약

| 클래스 | 메서드 | 설명 |
|---|---|---|
| `SignupTokenProvider` | `builder(provider,id,email)` | 🌟 fluent API 진입점 |
| | `generate(...)` | 단순 발급 (오버로드 4개: extras/TTL) |
| | `validate(token)` | JWT 서명 검증 |
| | `parse(token)` → `SignupTokenPayload` | 토큰 파싱 |
| `SignupTokenBuilder` | `.claim(k,v)` `.claims(map)` | 온보딩 필드 추가 |
| | `.ttl(millis)` `.ttl(Duration)` | 토큰 유효시간 오버라이드 |
| | `.build()` | 최종 JWT 문자열 |
| `SignupTokenService` | `save/get/validate/delete` | Redis 이중 검증 (조회만) |
| | `validateAndConsume` (Redis 6.2+) | 🌟 원자적 조회+삭제 — 회원가입 완료용 |
| `SignupTokenCookieWriter` | `write/read/clear` | HttpOnly 쿠키 관리 |
| | `CookieOptions` | per-call 쿠키 오버라이드 |
| `SignupTokenSecurity` | `corsForCookieAuth(...)` (static) | CORS 헬퍼 |
| | `cookieAuthCorsConfig(...)` (static) | raw CorsConfiguration |
| `SignupTokenPayload` | record | `provider, providerId, email, extras`, `extra(k)` |

---

## 6. Bean 커스터마이징

모든 자동 등록 빈은 `@ConditionalOnMissingBean`. 프로젝트에서 같은 타입 빈을 직접 등록하면 자동 대체됩니다.

```java
@Bean
public SignupTokenProvider signupTokenProvider(SignupTokenProperties props) {
    return new CustomSignupTokenProvider(props);  // 사용자 정의 우선
}
```

---

## 7. 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| 앱 시작 시 `IllegalStateException: signup-token.secret-key must be at least 32 bytes` | secret이 짧음. `openssl rand -base64 48` 로 새로 생성. **v1.0.0 부터 startup에서 fail-fast** |
| `IllegalArgumentException: Token is not a signup token (expected type=SIGNUP, got type=...)` | 같은 secret으로 발급된 다른 종류 JWT를 signup token 자리에 넣음. 정상 동작 (타입 검증 통과 못함) |
| 앱 시작 시 `IllegalStateException: signup-token.cookie.same-site=None requires cookie.secure=true` | HTTP 개발환경에서 발생. `application.yml` 에서 `cookie.same-site: Lax`, `cookie.secure: false` 로 변경 |
| `IllegalArgumentException: JWT String argument cannot be null or empty` | 쿠키에서 토큰을 못 읽음. `cookieWriter.read(request)` 반환값 null 체크 필요 |
| 쿠키가 브라우저에 안 실림 | `SameSite=None` 이면 `Secure=true` 필수. HTTPS 환경에서만 동작 |
| CORS preflight 실패 | `SignupTokenSecurity.corsForCookieAuth(...)` origin에 요청 origin이 매칭되는지 확인 |
| `validate()` 는 true인데 Redis validate는 false | Redis에서 이미 삭제됨 (재발급 시 이전 토큰 무효화됨). 정상 동작 |
| `SignupTokenService` 빈이 생성 안 됨 | `RedisTemplate<String,String>` 빈이 없음. `spring-boot-starter-data-redis` 추가 필요 |
| `secret-key` 를 어디에 저장? | 절대 코드/git에 커밋 금지. `.env` + `dotenv` 또는 AWS Secrets Manager / GitHub Actions Secrets 사용 |

---

## ⚠️ CSRF 리스크와 대응 (반드시 읽을 것)

signup token을 **쿠키**로 전달하는 이상 CSRF 공격 가능성이 존재합니다.
공격 시나리오:
1. 피해자가 이미 OAuth 콜백을 완료해 `signup_token` 쿠키를 보유 중
2. 공격자가 자기 사이트에 위조 폼을 심고 피해자 방문 유도
3. 피해자 브라우저가 자동으로 `signup_token` 쿠키를 실려 보내 → 피해자 명의로 회원가입 완료

**대응 (택 1 이상):**

| 방법 | 강도 | 언제 쓰나 |
|---|---|---|
| **SameSite=Lax** 로 다운그레이드 | 🟢 강력 | 서비스가 크로스도메인 온보딩이 필요 없을 때 (동일 도메인이면 이걸로 충분) |
| **Origin/Referer 헤더 검증** | 🟢 강력 | 크로스도메인 온보딩이 필요할 때 |
| **Double-submit CSRF 토큰** | 🟢 강력 | 프론트에서 헤더로 별도 토큰 전달 |
| Short TTL + 1회성 소비 | 🟡 부분 | 이 라이브러리가 기본 제공 (30분 + `validateAndConsume`) |

**Origin 검증 최소 예시** (Spring Filter):
```java
@Component
public class SignupOriginFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED = Set.of("https://web.example.com");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/api/auth/signup")) {
            String origin = req.getHeader("Origin");
            if (origin == null || !ALLOWED.contains(origin)) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
```

---

## 로깅/Actuator 안전성

- `SignupTokenProperties.toString()` 은 `secretKey` 를 `[MASKED]` 로 마스킹 → 실수로 log 찍어도 유출 없음
- Spring Boot Actuator `/env` 는 이름에 `secret`/`key` 포함된 프로퍼티를 자동 sanitize (기본값)
- 그래도 명시적으로 강화하려면:
```yaml
management:
  endpoint:
    env:
      show-values: never    # 또는 when-authorized
    configprops:
      show-values: never
```

---

## 보안 특징 요약
- ✅ **Fail-fast**: startup 시 secret 길이/존재/쿠키 조합(SameSite=None+Secure=false 거부) 검증
- ✅ **토큰 타입 강제**: `type=SIGNUP` 클레임 검증 → 다른 용도 JWT 재사용 차단
- ✅ **원자적 1회성 소비**: `validateAndConsume` = Redis `GETDEL` 로 race 방지
- ✅ **Timing-safe 비교**: `MessageDigest.isEqual` 로 사이드채널 방어
- ✅ **HttpOnly · SameSite=None · Secure** 쿠키 기본값 (HTTPS/크로스도메인 대응)
- ✅ **null 입력 fail-fast**: 발급 시 required 필드 명시적 거부
- ✅ **Secret 마스킹**: `toString()` 오버라이드로 로그 유출 방지
- ⚠️ **CSRF는 서비스 레벨 대응 필요** (위 CSRF 섹션 참조)

## 라이선스
MIT
