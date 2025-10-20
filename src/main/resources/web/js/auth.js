// 認証共通機能
class AuthService {
    constructor() {
        this.accessToken = null;
        this.refreshPromise = null;
    }
    
    // アクセストークンを取得（メモリ保持）
    getAccessToken() {
        // メモリにない場合はローカルストレージから取得
        if (!this.accessToken) {
            this.accessToken = localStorage.getItem('accessToken');
        }
        return this.accessToken;
    }
    
    // アクセストークンを設定
    setAccessToken(token) {
        this.accessToken = token;
        if (token) {
            localStorage.setItem('accessToken', token);
        } else {
            localStorage.removeItem('accessToken');
        }
        console.log('Access token set');
    }
    
    // アクセストークンの期限をチェック
    isExpiringSoon(thresholdSec = 120) {
        if (!this.accessToken) {
            return true;
        }
        
        try {
            const payload = this.parseJwtPayload(this.accessToken);
            const now = Math.floor(Date.now() / 1000);
            const remaining = payload.exp - now;
            
            console.log(`Token expires in ${remaining} seconds`);
            return remaining < thresholdSec;
        } catch (error) {
            console.error('Error parsing token:', error);
            return true;
        }
    }
    
    // JWTペイロードをパース
    parseJwtPayload(token) {
        const parts = token.split('.');
        if (parts.length !== 3) {
            throw new Error('Invalid token format');
        }
        
        const payload = parts[1];
        const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
        return JSON.parse(decoded);
    }
    
    // 単一フライトでリフレッシュトークンを更新
    async refreshOnce() {
        if (this.refreshPromise) {
            console.log('Refresh already in progress, waiting...');
            return await this.refreshPromise;
        }
        
        console.log('Starting token refresh...');
        this.refreshPromise = this.performRefresh();
        
        try {
            const result = await this.refreshPromise;
            return result;
        } finally {
            this.refreshPromise = null;
        }
    }
    
    // 実際のリフレッシュ処理
    async performRefresh() {
        try {
            const response = await fetch('/api/auth/refresh', {
                method: 'POST',
                credentials: 'include'
            });
            
            if (!response.ok) {
                throw new Error(`Refresh failed: ${response.status}`);
            }
            
            const data = await response.json();
            this.setAccessToken(data.accessToken);
            
            console.log('Token refreshed successfully');
            return data.accessToken;
        } catch (error) {
            console.error('Token refresh failed:', error);
            this.accessToken = null;
            throw error;
        }
    }
    
    // サインアウト
    async signout() {
        try {
            await fetch('/api/auth/signout', {
                method: 'POST',
                credentials: 'include'
            });
        } catch (error) {
            console.error('Signout request failed:', error);
        } finally {
            this.accessToken = null;
            localStorage.removeItem('accessToken');
            window.location.href = '/index.html';
        }
    }
}

// グローバルインスタンス
window.authService = new AuthService();
