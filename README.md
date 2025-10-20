# ミニ掲示板アプリケーション（JWT認証付き）

Grizzly HTTPサーバーとRedisを使用したJWT認証機能付きの掲示板アプリケーションです。

## 機能

- **トップ画面 (index.html)**: 投稿一覧の表示（公開）
- **投稿画面 (post.html)**: 新しい投稿の作成（要ログイン）
- **管理画面 (admin.html)**: 投稿の削除（管理者のみ）
- **サインイン画面 (signin.html)**: ユーザーログイン
- **サインアップ画面 (signup.html)**: 新規ユーザー登録

## 認証機能

- **JWT認証**: アクセストークン（10分）+ リフレッシュトークン（7日）
- **自動トークン更新**: 期限間近または401エラー時に自動リフレッシュ
- **単一フライト更新**: 並列リクエスト時の重複更新を防止
- **権限ベースUI**: ログイン状態と管理者権限に応じたUI制御
- **セキュアCookie**: リフレッシュトークンはHttpOnly Cookieで管理

## 技術スタック

- **Java 17**
- **Grizzly HTTP Server**: Webサーバー
- **Redis**: データベース
- **Jedis**: Redisクライアント
- **Jackson**: JSON処理
- **JWT (jjwt)**: 認証トークン
- **BCrypt**: パスワードハッシュ化
- **SLF4J + Logback**: ログ機能
- **HTML/CSS/JavaScript**: フロントエンド

## 前提条件

- Java 17以上
- Maven 3.6以上
- Redisサーバー

## セットアップ

1. Redisサーバーを起動:
```bash
redis-server
```

2. 環境変数を設定:
```bash
export JWT_HS256_SECRET="your-super-secret-jwt-key-change-this-in-production"
export JWT_ACCESS_TTL_SEC=600
export JWT_REFRESH_TTL_SEC=604800
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

3. プロジェクトをビルド:
```bash
mvn clean compile
```

4. アプリケーションを実行:

**VS Codeでのデバッグ実行（推奨）:**
- F5キーを押してデバッグ開始
- "Launch Mini Bulletin Board"を選択
- ホットリロード機能が自動的に有効

**Mavenでの実行:**
```bash
mvn exec:java -Dexec.mainClass="app.Main"
```

5. ブラウザでアクセス:
- http://localhost:8080 (トップ画面)
- http://localhost:8080/signin.html (サインイン)
- http://localhost:8080/signup.html (サインアップ)
- http://localhost:8080/post.html (投稿画面 - 要ログイン)
- http://localhost:8080/admin.html (管理画面 - 管理者のみ)

## 🔥 ホットリロード機能

VS Codeの統合機能を使用してホットリロードを実現します：

### 特徴

- **VS Code統合**: Extension Pack for Javaを使用
- **真のホットスワップ**: サーバー再起動なしでJavaコード変更を反映
- **デバッグ統合**: ブレークポイント、変数監視が可能
- **設定不要**: F5キーで即座に使用可能

### 使用方法

1. VS CodeでF5キーを押してデバッグ開始
2. Javaコードを編集・保存（Ctrl+S）
3. 自動的にホットスワップが実行される
4. ブラウザで変更を確認

### 制限事項

- **メソッドシグネチャ変更**: 不可（サーバー再起動が必要）
- **フィールド追加/削除**: 不可
- **コンストラクタ変更**: 不可
- **メソッド内ロジック変更**: ✅ 可能
- **ログメッセージ変更**: ✅ 可能

## API エンドポイント

### GET /api/posts
投稿一覧を取得

**レスポンス例:**
```json
[
  {
    "id": "uuid-string",
    "message": "投稿内容",
    "timestamp": 1640995200000
  }
]
```

### POST /api/posts
新しい投稿を作成

**リクエスト:**
- Content-Type: application/json
- Body: `{"message": "投稿内容"}`

**レスポンス例:**
```json
{
  "id": "uuid-string",
  "message": "投稿内容",
  "timestamp": 1640995200000
}
```

### DELETE /api/posts/:id
指定されたIDの投稿を削除

**レスポンス例:**
```json
{
  "success": true
}
```

## プロジェクト構成

```
src/
├── main/
│   ├── java/com/example/
│   │   ├── Main.java          # メインアプリケーション
│   │   ├── ApiHandler.java    # API処理
│   │   ├── Post.java          # データモデル
│   │   └── RedisService.java  # Redis操作クラス
│   └── resources/
│       ├── logback.xml        # ログ設定
│       └── web/
│           ├── index.html     # トップ画面
│           ├── post.html       # 投稿画面
│           └── admin.html      # 管理画面
└── test/
```

## 特徴

- **リアルタイム更新**: トップ画面は5秒ごとに自動更新
- **レスポンシブデザイン**: モバイル対応
- **エラーハンドリング**: 適切なエラーメッセージ表示
- **文字数制限**: 投稿は1000文字まで
- **XSS対策**: HTMLエスケープ処理
- **UTF-8対応**: 日本語文字化け対策済み
- **ログ機能**: SLF4J + Logbackによる詳細ログ
- **CORS対応**: クロスオリジンリクエスト対応

## 開発・デバッグ

### VS Codeでの開発

1. **デバッグ実行**: F5キーでデバッグ開始
2. **ホットリロード**: コード変更後Ctrl+Sで保存
3. **ブレークポイント**: 任意の行でデバッグ停止
4. **変数監視**: デバッグ中に変数値を確認

### ログ確認

- **コンソールログ**: VS Codeの統合ターミナルに表示
- **ファイルログ**: `logs/application.log`に保存
- **エラーログ**: `logs/error.log`に保存
- **ログレベル**: DEBUG、INFO、WARN、ERROR

### Redisデータ確認
```bash
redis-cli --raw
> KEYS *
> HGETALL posts
```

### ポート変更
`Main.java`の`PORT`定数を変更してください。

### VS Code設定

プロジェクトには以下の設定ファイルが含まれています：

- `.vscode/launch.json`: デバッグ設定
- `.vscode/settings.json`: プロジェクト固有設定
- `.devcontainer/devcontainer.json`: コンテナ環境設定

## トラブルシューティング

### 文字化けが発生する場合
- ブラウザの文字エンコーディングをUTF-8に設定
- サーバー側でUTF-8設定が正しく適用されているか確認

### Redis接続エラー
- Redisサーバーが起動しているか確認
- ポート6379が使用可能か確認

### ホットリロードが動作しない場合
- VS CodeのJava Extension Packがインストールされているか確認
- F5キーでデバッグモードで起動しているか確認

## ライセンス

MIT License