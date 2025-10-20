// UI制御（権限ベース）
class UIService {
    constructor() {
        this.authService = window.authService;
        this.apiService = window.apiService;
        this.currentUser = null;
        this.isLoggedIn = false;
    }
    
    // 初期化（起動時に認証状態を確認）
    async init() {
        try {
            console.log('Initializing UI service...');
            await this.checkAuthStatus();
            this.updateUI();
        } catch (error) {
            console.error('Failed to initialize UI service:', error);
            this.isLoggedIn = false;
            this.updateUI();
        }
    }
    
    // 認証状態を確認
    async checkAuthStatus() {
        try {
            // アクセストークンが無い場合は未ログイン状態
            if (!this.authService.getAccessToken()) {
                console.log('No access token found, user not authenticated');
                this.currentUser = null;
                this.isLoggedIn = false;
                return;
            }
            
            // アクセストークンからユーザー情報を取得（ロール情報のため）
            try {
                const payload = this.authService.parseJwtPayload(this.authService.getAccessToken());
                this.currentUser = {
                    sub: payload.sub,
                    username: payload.username,
                    roles: payload.roles
                };
                this.isLoggedIn = true;
                console.log('User authenticated:', payload.username, 'Roles:', payload.roles);
            } catch (error) {
                console.log('Invalid access token:', error.message);
                this.currentUser = null;
                this.isLoggedIn = false;
                this.authService.setAccessToken(null);
            }
        } catch (error) {
            console.log('Error checking auth status:', error.message);
            this.currentUser = null;
            this.isLoggedIn = false;
            this.authService.setAccessToken(null);
        }
    }
    
    // UIを更新（権限に基づいてボタン表示制御）
    updateUI() {
        console.log('Updating UI - Logged in:', this.isLoggedIn, 'User:', this.currentUser?.username);
        
        // 投稿ボタンの制御
        const postButton = document.getElementById('postButton');
        if (postButton) {
            if (this.isLoggedIn) {
                postButton.style.display = 'inline-block';
                console.log('Post button shown');
            } else {
                postButton.style.display = 'none';
                console.log('Post button hidden');
            }
        }
        
        // 管理ボタンの制御
        const adminButton = document.getElementById('adminButton');
        if (adminButton) {
            if (this.isLoggedIn && this.currentUser && this.currentUser.roles.includes('admin')) {
                adminButton.style.display = 'inline-block';
                console.log('Admin button shown');
            } else {
                adminButton.style.display = 'none';
                console.log('Admin button hidden');
            }
        }
        
        // ログイン/ログアウトボタンの制御
        const loginButton = document.getElementById('loginButton');
        const logoutButton = document.getElementById('logoutButton');
        
        if (loginButton) {
            loginButton.style.display = this.isLoggedIn ? 'none' : 'inline-block';
        }
        
        if (logoutButton) {
            logoutButton.style.display = this.isLoggedIn ? 'inline-block' : 'none';
        }
        
        // ユーザー名表示
        const usernameDisplay = document.getElementById('usernameDisplay');
        if (usernameDisplay) {
            if (this.isLoggedIn && this.currentUser) {
                usernameDisplay.textContent = `Welcome, ${this.currentUser.username}`;
                usernameDisplay.style.display = 'inline-block';
            } else {
                usernameDisplay.style.display = 'none';
            }
        }
    }
    
    // ログイン成功時の処理
    onLoginSuccess(accessToken) {
        this.authService.setAccessToken(accessToken);
        this.isLoggedIn = true;
        this.updateUI();
        
        // トップページにリダイレクト
        window.location.href = '/index.html';
    }
    
    // ログアウト処理
    async logout() {
        try {
            await this.authService.signout();
        } catch (error) {
            console.error('Logout failed:', error);
        } finally {
            this.isLoggedIn = false;
            this.currentUser = null;
            this.updateUI();
        }
    }
    
    // 管理者権限チェック
    isAdmin() {
        return this.isLoggedIn && this.currentUser && this.currentUser.roles.includes('admin');
    }
    
    // ログイン状態チェック
    isAuthenticated() {
        return this.isLoggedIn;
    }
    
    // 現在のユーザー情報を取得
    getCurrentUser() {
        return this.currentUser;
    }
}

// グローバルインスタンス
window.uiService = new UIService();

document.addEventListener('DOMContentLoaded', () => {
    window.uiService.init();
});
