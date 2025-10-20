// API リクエストラッパー（単一フライト更新機能付き）
class ApiService {
    constructor() {
        this.authService = window.authService;
    }
    
    // メインのリクエストメソッド
    async request(input, init = {}) {
        // 認証が必要なAPIかどうかをチェック
        const isAuthRequired = this.isAuthRequiredApi(input, init.method || 'GET');
        
        // 1. 認証が必要なAPIの場合のみ、アクセストークンをチェック・リフレッシュ
        if (isAuthRequired) {
            if (!this.authService.getAccessToken() || this.authService.isExpiringSoon()) {
                console.log('Token missing or expiring soon, refreshing...');
                try {
                    await this.authService.refreshOnce();
                } catch (error) {
                    console.error('Failed to refresh token:', error);
                    // リフレッシュに失敗した場合はログインページへ
                    window.location.href = '/signin.html';
                    return;
                }
            }
        }
        
        // 2. AuthorizationヘッダーにBearerトークンを設定（認証が必要なAPIのみ）
        const headers = {
            'Content-Type': 'application/json',
            ...init.headers
        };
        
        if (isAuthRequired && this.authService.getAccessToken()) {
            headers['Authorization'] = `Bearer ${this.authService.getAccessToken()}`;
        }
        
        const requestInit = {
            ...init,
            headers,
            credentials: 'include'
        };
        
        // 3. リクエストを送信
        let response;
        try {
            response = await fetch(input, requestInit);
        } catch (error) {
            console.error('Request failed:', error);
            throw error;
        }
        
        // 4. 401エラーの場合、認証が必要なAPIのみリフレッシュして再送
        if (response.status === 401 && isAuthRequired) {
            const errorData = await response.json().catch(() => ({}));
            if (errorData.error === 'token_expired') {
                console.log('Token expired, refreshing and retrying...');
                try {
                    await this.authService.refreshOnce();
                    
                    // 新しいトークンで再送
                    headers['Authorization'] = `Bearer ${this.authService.getAccessToken()}`;
                    const retryInit = {
                        ...init,
                        headers,
                        credentials: 'include'
                    };
                    
                    response = await fetch(input, retryInit);
                } catch (refreshError) {
                    console.error('Refresh failed on retry:', refreshError);
                    window.location.href = '/signin.html';
                    return;
                }
            }
        }
        
        return response;
    }
    
    // JSONレスポンスのラッパー
    async json(input, init = {}) {
        const response = await this.request(input, init);
        
        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`HTTP ${response.status}: ${errorText}`);
        }
        
        return await response.json();
    }
    
    // GETリクエスト
    async get(url) {
        return await this.json(url, { method: 'GET' });
    }
    
    // POSTリクエスト
    async post(url, data) {
        return await this.json(url, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    }
    
    // DELETEリクエスト
    async delete(url) {
        return await this.json(url, { method: 'DELETE' });
    }
    
    // 認証が必要なAPIかどうかを判定
    isAuthRequiredApi(url, method = 'GET') {
        // 認証APIは全て公開
        if (url.startsWith('/api/auth/')) {
            return false;
        }
        
        // GET /api/posts は公開
        if (method === 'GET' && url === '/api/posts') {
            return false;
        }
        
        // POST /api/posts と DELETE /api/posts/{id} は認証必要
        if (url.startsWith('/api/posts')) {
            return true;
        }
        
        // その他のAPIは認証必要
        return true;
    }
}

// グローバルインスタンス
window.apiService = new ApiService();
