/**
 * Common utility functions for the application
 */

const Utils = {
    // Show an alert message
    showAlert(message, type = 'success', container = '#alert-container', timeout = 5000) {
        const alertContainer = document.querySelector(container);
        if (!alertContainer) return;

        // Create alert element
        const alert = document.createElement('div');
        alert.className = `alert alert-${type}`;
        alert.textContent = message;

        // Clear existing alerts
        alertContainer.innerHTML = '';

        // Append new alert
        alertContainer.appendChild(alert);

        // Auto-hide after timeout
        if (timeout > 0) {
            setTimeout(() => {
                alert.remove();
            }, timeout);
        }
    },

    // Format a date
    formatDate(dateString) {
        if (!dateString) return '';
        const date = new Date(dateString);
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
    },

    // Initialize modals
    initModals() {
        // Close modal when clicking outside of it
        document.querySelectorAll('.modal').forEach(modal => {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    this.closeModal(modal.id);
                }
            });

            // Close button functionality
            const closeBtn = modal.querySelector('.close');
            if (closeBtn) {
                closeBtn.addEventListener('click', () => {
                    this.closeModal(modal.id);
                });
            }
        });
    },

    // Open a modal
    openModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.style.display = 'block';
        }
    },

    // Close a modal
    closeModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.style.display = 'none';

            // Reset form if exists
            const form = modal.querySelector('form');
            if (form) form.reset();
        }
    },

    // Check if user is authenticated, redirect if not
    checkAuth() {
        if (!API.isAuthenticated()) {
            window.location.href = '/login.html';
            return false;
        }
        return true;
    },

    // Check if user is admin, redirect if not
    checkAdmin() {
        if (!API.isAdmin()) {
            window.location.href = '/profile.html';
            return false;
        }
        return true;
    },

    // Update navigation based on user role
    updateNavigation() {
        const user = API.getCurrentUser();
        const adminNav = document.getElementById('admin-nav');
        const userNav = document.getElementById('user-nav');
        const usernameEl = document.getElementById('current-username');

        if (user) {
            // Show/hide admin links
            if (adminNav) {
                adminNav.style.display = user.isAdmin ? 'block' : 'none';
            }

            // Update username display
            if (usernameEl) {
                usernameEl.textContent = user.username;
            }

            // Show user nav
            if (userNav) {
                userNav.style.display = 'block';
            }
        } else {
            // Hide all authenticated navigation
            if (adminNav) adminNav.style.display = 'none';
            if (userNav) userNav.style.display = 'none';
        }
    },

    // Handle form submission
    handleFormSubmit(formElement, submitCallback) {
        formElement.addEventListener('submit', async (e) => {
            e.preventDefault();

            try {
                // Disable submit button
                const submitBtn = formElement.querySelector('button[type="submit"]');
                if (submitBtn) {
                    submitBtn.disabled = true;
                    submitBtn.innerHTML = '<span class="loading"></span> Processing...';
                }

                // Call the callback with form data
                await submitCallback(formElement);

            } catch (error) {
                this.showAlert(error.message || 'An error occurred', 'danger');
                console.error(error);
            } finally {
                // Re-enable submit button
                const submitBtn = formElement.querySelector('button[type="submit"]');
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.textContent = submitBtn.dataset.originalText || 'Submit';
                }
            }
        });

        // Store original button text
        const submitBtn = formElement.querySelector('button[type="submit"]');
        if (submitBtn) {
            submitBtn.dataset.originalText = submitBtn.textContent;
        }
    },

    // Convert form data to object
    formToObject(form) {
        const formData = new FormData(form);
        const data = {};

        for (const [key, value] of formData.entries()) {
            // Handle checkboxes
            if (form.elements[key].type === 'checkbox') {
                data[key] = form.elements[key].checked;
            } else {
                data[key] = value;
            }
        }

        return data;
    },

    // Fill form with data
    fillFormWithData(form, data) {
        for (const key in data) {
            const input = form.elements[key];
            if (!input) continue;

            if (input.type === 'checkbox') {
                input.checked = !!data[key];
            } else {
                input.value = data[key] || '';
            }
        }
    },

    // Initialize page
    initPage() {
        this.checkAuth();
        this.updateNavigation();
        this.initModals();

        // Setup logout button
        const logoutBtn = document.getElementById('logout-btn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', (e) => {
                e.preventDefault();
                API.logout();
            });
        }
    }
};
