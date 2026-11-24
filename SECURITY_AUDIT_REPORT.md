# LocalVault セキュリティ監査＆脆弱性チェックレポート

## 2026-11-24（第1回 フルセキュリティ監査＆自動修正）

### 1. 機密情報・Git追跡・マニフェスト検査
- **Git 機密ファイル保護**: ✅ 合格（`localvault-release.jks`, `keystore.properties`, `local.properties`, `*.apk`, `*.lvault` はすべて `.gitignore` により除外されており、Git追跡対象への混入なし）
- **完全オフライン保証**: ✅ 合格（`AndroidManifest.xml` にて `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE` を `tools:node="remove"` で強制排除）
- **クラウドバックアップ・平文通信排除**: ✅ 合格（`allowBackup="false"`, `fullBackupContent="false"`, `data_extraction_rules.xml` 全ドメイン除外, `usesCleartextTraffic="false"`）
- **画面キャプチャ防止**: ✅ 合格（`MainActivity` および `AutofillAuthActivity` の両方で `WindowManager.LayoutParams.FLAG_SECURE` 設定済み）

### 2. 暗号化・認証・セッション管理診断
- **鍵導出・暗号スイート**: ✅ 合格（`PBKDF2WithHmacSHA256` 600,000回反復 + `AES-256-GCM` 96-bit ランダム IV / 128-bit Tag + `SQLCipher` AES-256 二重暗号化）
- **生体認証鍵管理**: ✅ 合格（Android Keystore `BIOMETRIC_STRONG` + `setInvalidatedByBiometricEnrollment(true)` + `CryptoObject` 結合）
- **メモリゼロクリア**: ✅ 合格（`VaultSessionManager` ロック時および `CryptoManager` 復号・鍵導出後の `finally` ブロックで `zeroize` 徹底）

### 3. 今回検出されたリスクと自動修正内容（4件修正済み）
1. **Autofill 保存時の Intent Extra 平文残留防止** (`AutofillAuthActivity.kt`)
   - ロック中のパスワード保存時に `Intent` 経由で渡された `EXTRA_SAVE_PASSWORD` / `EXTRA_SAVE_USERNAME` を、`onCreate` での読み取り直後に `intent.removeExtra()` で即座に破棄するよう自動修正。
2. **共有ホスティング（PaaS）ドメインおよび Userinfo 偽装URLへの自動入力防御強化** (`DomainMatcher.kt`)
   - `github.io`, `vercel.app`, `pages.dev`, `netlify.app`, `web.app`, `firebaseapp.com`, `appspot.com` 等の共有ドメインをマルチパート境界に追加し、別テナント（例：`alice.github.io` と `bob.github.io`）間で認証情報が誤マッチしないよう強化。
   - `https://trusted.com@evil.com/login` のような Userinfo 偽装URLから確実に実際のホスト（`evil.com`）のみを抽出するよう正規化処理を強化。
3. **暗号化バックアップ復元時の巨大ファイル（OOM DoS）防御** (`BackupCryptoManager.kt`)
   - 悪意ある巨大ファイル読み込みによるメモリ枯渇を防ぐため、`MAX_BACKUP_JSON_LENGTH`（50MB上限）チェックを追加。
4. **フィッシングURL・共有ドメイン分離の回帰テスト追加** (`DomainMatcherTest.kt`)
   - 上記のセキュリティ強化に対する単体テストケースを追加し、`./gradlew testDebugUnitTest` で全テスト通過を確認。
