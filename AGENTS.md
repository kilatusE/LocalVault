# LocalVault プロジェクト専用セキュリティ＆開発ルール

このリポジトリ（LocalVault）でコードの設計・実装・修正・監査を行う際は、以下のルールを常に厳守すること。

## 1. 完全オフライン・ネットワーク排除の厳守
- `app/src/main/AndroidManifest.xml` に通信権限（`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`）を追加してはならない。既存の `tools:node="remove"` による強制排除を維持すること。
- ネットワーク通信を行うライブラリやコード（HTTPクライアント、アナリティクス、クラッシュレポート送信など）を一切導入しないこと。

## 2. 暗号化・鍵管理・メモリ保護の厳守
- 鍵導出は `PBKDF2WithHmacSHA256`（デフォルト 600,000 回反復）、暗号化は `AES-256-GCM`（96-bit ランダム IV / 128-bit 認証タグ）、DB は SQLCipher による二重暗号化を維持すること。
- 暗号化鍵（DEK / KEK）やマスターパスワード、復号済み平文バイト列は、使用後に必ず `try / finally` 内で `CryptoManager.zeroize()` によりゼロクリアすること。
- 平文のマスターパスワード、DEK、復号済みパスワードをログ（`Log.*`, `println` 等）や例外メッセージ、Toast に出力してはならない。
- バックアップ復号（`BackupCryptoManager`）では、反復回数・ソルト長・IV長・サイズ上限の検証を維持すること。

## 3. UI・OS連携・Autofill の防御
- すべての Activity（`MainActivity`, `AutofillAuthActivity` 等）で `WindowManager.LayoutParams.FLAG_SECURE` を設定し、スクリーンショット・画面録画・履歴サムネイルを禁止すること。
- `AndroidManifest.xml` の `allowBackup="false"`, `fullBackupContent="false"`, `usesCleartextTraffic="false"` および `data_extraction_rules.xml` の全ドメイン除外を維持すること。
- `Intent` Extra に一時的に載せた機密情報は、受け取り直後に `intent.removeExtra()` で破棄すること。
- `DomainMatcher` では共有ホスティング（`github.io`, `vercel.app` 等）の別テナント分離および Userinfo 偽装 URL（`https://good.com@evil.com`）への対策を維持すること。

## 4. 機密ファイルの Git 流出防止
- `*.jks`, `*.keystore`, `keystore.properties`, `signing.properties`, `local.properties`, `*.apk`, `*.lvault` を絶対に Git にステージング・コミットしてはならない。`.gitignore` の設定を常に維持すること。

## 5. 変更後の必須検証
- `app/src/` または `gradle/` 配下のコード・設定を変更した場合は、完了前に必ず `.\gradlew.bat testDebugUnitTest` を実行し、すべてのセキュリティテスト（`ManifestSecurityTest`, `CryptoManagerTest`, `BackupCryptoManagerTest`, `DomainMatcherTest`, `DestructiveFuzzAndStressUnitTest` 等）がパスすることを確認すること。
