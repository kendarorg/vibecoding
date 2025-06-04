/**
 * API utility functions for the application
 */

const API = {
    // Base URL for API calls
    baseUrl: '/api',

    // Get the JWT token from local storage
    getToken() {
        return localStorage.getItem('jwt_token');
    },

    // Set the JWT token in local storage
    setToken(token) {
        localStorage.setItem('jwt_token', token);
    },

    // Remove the JWT token from local storage
    removeToken() {
        localStorage.removeItem('jwt_token');
    },

    // Get the current user from local storage
    getCurrentUser() {
        const userStr = localStorage.getItem('current_user');
        return userStr ? JSON.parse(userStr) : null;
    },

    // Set the current user in local storage
    setCurrentUser(user) {
        localStorage.setItem('current_user', JSON.stringify(user));
    },

    // Remove the current user from local storage
    removeCurrentUser() {
        localStorage.removeItem('current_user');
    },

    // Check if the user is authenticated
    isAuthenticated() {
        return !!this.getToken();
    },

    // Check if the user is an admin
    isAdmin() {
        const user = this.getCurrentUser();
        return user && user.isAdmin;
    },

    // Generic fetch method with authorization
    async fetch(endpoint, options = {}) {
        const url = `${this.baseUrl}${endpoint}`;

        // Set up headers with authorization if token exists
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };

        if (this.getToken()) {
            headers['X-Auth-Token'] = `${this.getToken()}`;
        }

        try {
            const response = await fetch(url, {
                ...options,
                headers
            });

            // Handle unauthorized responses
            if (response.status === 401 || response.status === 403) {
                this.removeToken();
                this.removeCurrentUser();
                window.location.href = '/login.html';
                
                return null;
            }

            // For non-JSON responses
            if (response.status === 204) {
                return { success: true };
            }

            // Parse JSON response
            const data = await response.json();

            // Check if request was successful
            if (!response.ok) {
                throw new Error(data.message || 'An error occurred');
            }

            return data;
        } catch (error) {
            console.error('API Error:', error);
            throw error;
        }
    },

    // Authentication
    async login(username, password) {
        const data = await this.fetch('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ username, password })
        });

        
        if (data && data.token) {
            this.setToken(data.token);
            this.setCurrentUser({
                username: data.username,
                userId: data.userId,
                isAdmin: data.isAdmin
            });
        }

        return data;
    },

    // Logout - client side only
    logout() {
        this.removeToken();
        this.removeCurrentUser();
        window.location.href = '/login.html';
        
    },

    // Users
    async getUsers() {
        return this.fetch('/settings/users');
    },

    async getUser(id) {
        return this.fetch(`/settings/users/${id}`);
    },

    async createUser(user) {
        return this.fetch('/settings/users', {
            method: 'POST',
            body: JSON.stringify(user)
        });
    },

    async updateUser(id, user) {
        return this.fetch(`/settings/users/${id}`, {
            method: 'PUT',
            body: JSON.stringify(user)
        });
    },

    async deleteUser(id) {
        return this.fetch(`/settings/users/${id}`, {
            method: 'DELETE'
        });
    },

    // Backup Folders
    async getBackupFolders() {
        return this.fetch('/settings/folders');
    },

    async getBackupFolder(name) {
        return this.fetch(`/settings/folders/${name}`);
    },

    async createBackupFolder(folder) {
        return this.fetch('/settings/folders', {
            method: 'POST',
            body: JSON.stringify(folder)
        });
    },

    async updateBackupFolder(name, folder) {
        return this.fetch(`/settings/folders/${name}`, {
            method: 'PUT',
            body: JSON.stringify(folder)
        });
    },

    async deleteBackupFolder(name) {
        return this.fetch(`/settings/folders/${name}`, {
            method: 'DELETE'
        });
    },

    // Server Settings
    async getSettings() {
        return this.fetch('/settings');
    },

    async updateSettings(settings) {
        return this.fetch('/settings', {
            method: 'PUT',
            body: JSON.stringify(settings)
        });
    },

    async updatePort(port) {
        return this.fetch('/settings/port', {
            method: 'PUT',
            body: JSON.stringify(port)
        });
    },

    async updateMaxPacketSize(maxPacketSize) {
        return this.fetch('/settings/maxPacketSize', {
            method: 'PUT',
            body: JSON.stringify(maxPacketSize)
        });
    },

    async updateMaxConnections(maxConnections) {
        return this.fetch('/settings/maxConnections', {
            method: 'PUT',
            body: JSON.stringify(maxConnections)
        });
    }
};
