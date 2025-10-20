# 目的

Grizzly ベースのミニ掲示板（3画面: トップ/投稿/管理）。**JWT 認証必須**。**アクセストークン10分**、**リフレッシュ7日**。リクエスト都度で期限確認し、切れていれば**単一フライト**で更新。権限により UI 制御。

# ディレクトリ

```
src/main/java/app/
  Main.java
  handlers/
    StaticHandler.java         // SPA 非対応でOK、通常静的配信
    ApiAuthHandler.java        // /api/auth/*
    ApiPostsHandler.java       // /api/posts/*
  security/
    JwtService.java
    RefreshService.java
    AuthFilter.java            // /api/* に適用（公開API除外可）
  store/
    RedisClient.java
    UserRepo.java
    PostRepo.java
src/main/resources/web/
  index.html      // トップ（公開）
  post.html       // 投稿（要ログイン）
  admin.html      // 管理（管理者のみ）
  signin.html     // サインイン
  signup.html     // サインアップ
  js/
    auth.js       // 認証共通（必須）
    api.js        // fetch ラッパ + 単一フライト更新
    ui.js         // ボタン表示制御（権限）
```

# 環境変数

* `JWT_ACCESS_TTL_SEC=600`（10分）
* `JWT_REFRESH_TTL_SEC=604800`（7日）
* `JWT_HS256_SECRET`（必須）
* `REDIS_HOST`, `REDIS_PORT`

# ルーティング（Grizzly）

* 静的: `/` → `StaticHttpHandler("src/main/resources/web")`
* 認証API: `/api/auth/*` → `ApiAuthHandler`

  * `POST /api/auth/signup` {username,email,password} → 201
  * `POST /api/auth/signin`  {username,password} → access(Body), refresh(HttpOnly Cookie)
  * `POST /api/auth/refresh` Cookie の RT → 新 AT + 新 RT（ローテーション）
  * `POST /api/auth/signout` RT 失効、Cookie クリア
  * `GET  /api/auth/me` → {sub, username, roles}
* 投稿API: `/api/posts/*` → `ApiPostsHandler`

  * `GET  /api/posts` → 公開（トップで使用）
  * `POST /api/posts` {message} → 認証必須
  * `DELETE /api/posts/{id}` → **admin 権限**のみ

# データモデル

* User: `{id(UUID), username, email, pass_hash, roles(set: ["user","admin"]) }`
* Post: `{id, message, created, userId}`
* Redis 例

  * `user:{id}` Hash
  * `user:byname:{username} = {id}`
  * `post:{id}` Hash
  * `posts` ZSET(score=created, member=id)
  * `rt:active:{jti}` = userId（TTL=RT有効期限）   // RTホワイトリスト
  * `rt:black:{jti}` = 1（TTL=残余期限）           // 失効済み

# セキュリティ設計

* アクセストークン(AT): **Bearer**（ヘッダ）。**TTL=10分**。クレーム: `sub, username, roles, iat, exp, jti`
* リフレッシュ(RT): **HttpOnly+Secure+SameSite=Lax** Cookie。**TTL=7日**。毎回 **ローテーション**。`rt:active:{jti}` で認可し、使用した RT は失効（`black` へ移動）
* 検証時計ずれ: ±60秒許容
* ログアウト/権限変更時: 対象 RT を失効。必要なら AT の `jti` ブラックリストも導入可（任意）

# Java 実装要点

* `JwtService`:

  * `String issueAccess(User u)` / `issueRefresh(User u)`（異なる `jti`）
  * `Claims verifyAccess(token)` / `verifyRefresh(token)`（HS256）
* `RefreshService`:

  * `rotate(refreshToken)` → 検証→`rt:active`確認→旧`jti`失効→新 RT+AT 発行→`rt:active`登録
* `AuthFilter`（/api/* にチェーン）:

  * 公開パス（`/api/auth/*`, `GET /api/posts`）は素通し
  * それ以外は Authorization: Bearer を検証→`request.setAttribute("auth", claims)`
* `ApiAuthHandler`:

  * `signin`: 認証→AT を JSON で返し、RT は Cookie 設定
  * `refresh`: Cookie から RT→`RefreshService.rotate()`→新 AT を返却
* `ApiPostsHandler`:

  * `POST/DELETE` は `AuthFilter` で `auth` 必須。`roles` に `admin` が無いと `/DELETE` は 403

# フロント（共通 JS）

`js/auth.js`（必須仕様）

* `getAccessToken()`：メモリ保持。期限(`exp`)をパースして返す
* `setAccessToken(token)`：保存
* `isExpiringSoon(thresholdSec=120)`：残り < 2分
* `refreshOnce()`：**単一フライト**。進行中の Promise を共有

`js/api.js`

* `request(input, init)`:

  1. AT が無い/期限間近 → `/api/auth/refresh`
  2. Authorization ヘッダに `Bearer ${AT}`
  3. 401(token_expired) 受信時だけ一度だけ `refreshOnce()`→元リクエスト再送
  4. 多重実行は `refreshOnce` の Promise を await して待機
* 返却は `json()` ラッパ

`js/ui.js`

* 起動時に `/api/auth/me` を**失敗許容**で叩き、ログイン状態と `roles` を得る
* トップ画面: 未ログインなら「投稿」「管理」ボタンを **非表示**
* ログイン済み: 「投稿」表示。`roles` に `admin` があれば「管理」表示
* サインイン/アップ画面: フォーム送信後に `setAccessToken()`、以後 UI を更新

# 画面仕様

* **トップ** `/index.html`:

  * 初回 `GET /api/posts` で一覧描画
  * ボタン可視制御は `ui.js` に委譲
* **投稿** `/post.html`:

  * 送信時 `api.request('/api/posts', {method:'POST', body: JSON.stringify({message})})`
* **管理** `/admin.html`:

  * `GET /api/posts` + 各行に削除ボタン
  * 削除は `api.request('/api/posts/{id}', {method:'DELETE'})`（admin 以外は 403 を UI 通知）
* **サインイン** `/signin.html`:

  * `POST /api/auth/signin` → AT 受領、RT は Cookie。`setAccessToken()`→トップへ
* **サインアップ** `/signup.html`:

  * `POST /api/auth/signup` → 自動サインインも可（任意）

# 受け入れ基準

* 未ログインで `/index.html` は表示でき、**投稿/管理ボタンは非表示**
* サインイン後は **投稿ボタン表示**、admin ユーザーは **管理ボタン表示**
* AT 10分。期限 2 分前または 401 で **自動リフレッシュ**。並列 10 リクエストで **更新は1回のみ**、他は待機
* サインアウトで RT 失効。以後すべての API が 401（トップは閲覧可）
* `DELETE /api/posts/{id}` は admin 以外 403

# 注意

* RT は **LocalStorage に保存しない**。**HttpOnly Cookie** のみ
* CORS が必要なら `/api/*` に限定し `Allow-Credentials` 設定
* すべての JSON 応答は `application/json; charset=UTF-8`、UTF-8 固定

この仕様で実装を開始せよ。
