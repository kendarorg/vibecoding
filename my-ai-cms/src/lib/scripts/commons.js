
// Create overlay elements (but don't add to DOM yet)
const overlay = document.createElement('div');
overlay.className = 'paste-overlay';
overlay.style.display = 'none';

const spinner = document.createElement('div');
spinner.className = 'paste-spinner';

const message = document.createElement('div');
message.textContent = 'Processing...';

overlay.appendChild(spinner);
overlay.appendChild(message);
document.body.appendChild(overlay);

// Show/hide overlay functions
function showPasteOverlay() {
    overlay.style.display = 'flex';
}

function hidePasteOverlay() {
    overlay.style.display = 'none';
}

// Helper function to display messages
function showMessage(type,label,message) {
    const errorDiv = document.createElement('div');
    errorDiv.className = type+'-notification notification';
    errorDiv.innerHTML = `<strong>${label}:</strong> ${message}`;
    document.body.appendChild(errorDiv);

    // Auto-remove after 5 seconds
    setTimeout(() => {
        errorDiv.style.opacity = '0';
        setTimeout(() => {
            document.body.removeChild(errorDiv);
        }, 500);
    }, 5000);
}

function showError(message){
    showMessage('error','Error',message);
}
function showWarning(message){
    showMessage('warn','Warning',message);
}
function showNotification(message){
    showMessage('notification','Notification',message);
}

class FloatingDialog {
    static #dialogContainer = null;

    static #createContainer() {
        if (this.#dialogContainer) return this.#dialogContainer;

        this.#dialogContainer = document.createElement('div');
        this.#dialogContainer.className = 'floating-dialog-container';

        document.body.appendChild(this.#dialogContainer);

        return this.#dialogContainer;
    }

    static prompt(message, defaultValue = '') {
        return new Promise((resolve, reject) => {
            const container = this.#createContainer();

            const dialog = document.createElement('div');
            dialog.className = 'floating-dialog';

            const title = document.createElement('div');
            title.className = 'floating-dialog-title';
            title.textContent = message;

            const input = document.createElement('input');
            input.className = 'floating-dialog-input';
            input.value = defaultValue;
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    resolve(input.value);
                    this.#closeDialog();
                } else if (e.key === 'Escape') {
                    reject();
                    this.#closeDialog();
                }
            });

            const buttons = document.createElement('div');
            buttons.className = 'floating-dialog-buttons';

            const cancelButton = document.createElement('button');
            cancelButton.className = 'floating-dialog-button floating-dialog-button-cancel';
            cancelButton.textContent = 'Cancel';
            cancelButton.addEventListener('click', () => {
                reject();
                this.#closeDialog();
            });

            const confirmButton = document.createElement('button');
            confirmButton.className = 'floating-dialog-button floating-dialog-button-confirm';
            confirmButton.textContent = 'OK';
            confirmButton.addEventListener('click', () => {
                resolve(input.value);
                this.#closeDialog();
            });

            buttons.appendChild(cancelButton);
            buttons.appendChild(confirmButton);

            dialog.appendChild(title);
            dialog.appendChild(input);
            dialog.appendChild(buttons);

            container.innerHTML = '';
            container.appendChild(dialog);

            // Focus the input after rendering
            setTimeout(() => input.focus(), 0);
        });
    }

    static confirm(message) {
        return new Promise((resolve, reject) => {
            const container = this.#createContainer();

            const dialog = document.createElement('div');
            dialog.className = 'floating-dialog';

            const title = document.createElement('div');
            title.className = 'floating-dialog-title';
            title.textContent = message;

            const buttons = document.createElement('div');
            buttons.className = 'floating-dialog-buttons';

            const cancelButton = document.createElement('button');
            cancelButton.className = 'floating-dialog-button floating-dialog-button-cancel';
            cancelButton.textContent = 'Cancel';
            cancelButton.addEventListener('click', () => {
                reject();
                this.#closeDialog();
            });

            const confirmButton = document.createElement('button');
            confirmButton.className = 'floating-dialog-button floating-dialog-button-confirm';
            confirmButton.textContent = 'OK';
            confirmButton.addEventListener('click', () => {
                resolve();
                this.#closeDialog();
            });

            buttons.appendChild(cancelButton);
            buttons.appendChild(confirmButton);

            dialog.appendChild(title);
            dialog.appendChild(buttons);

            container.innerHTML = '';
            container.appendChild(dialog);

            // Focus the confirm button after rendering
            setTimeout(() => confirmButton.focus(), 0);

            window.addEventListener('keydown', function onKeyDown(e) {
                if (e.key === 'Enter') {
                    resolve();
                    this.#closeDialog();
                    window.removeEventListener('keydown', onKeyDown);
                } else if (e.key === 'Escape') {
                    reject();
                    this.#closeDialog();
                    window.removeEventListener('keydown', onKeyDown);
                }
            }.bind(this));
        });
    }

    static #closeDialog() {

        if (this.#dialogContainer) {
            this.#dialogContainer.innerHTML = '';
        }
        document.body.removeChild(this.#dialogContainer);
        this.#dialogContainer=null;
    }
}

function floatingConfirm(message,cbk) {
    FloatingDialog.confirm(message)
        .then(() => {
            // User clicked OK
            cbk(true);
        })
        .catch(() => {
            // User clicked Cancel or pressed Escape
            cbk(false);
        });
}

function floatingPrompt(message,cbk,defaultValue='') {
    FloatingDialog.prompt(message,defaultValue)
        .then(name => {
            cbk(true,name);
        })
        .catch(() => {
            cbk(false)
        });
}