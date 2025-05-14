// Add this to your CSS (you can place it in storage.css or inline it)
const pasteOverlayStyles = `
.paste-overlay {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(0, 0, 0, 0.5);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    z-index: 9999;
    color: white;
    font-size: 18px;
}

.paste-spinner {
    border: 4px solid rgba(255, 255, 255, 0.3);
    border-radius: 50%;
    border-top: 4px solid white;
    width: 40px;
    height: 40px;
    animation: spin 1s linear infinite;
    margin-bottom: 15px;
}

@keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
}
`;

// Add the styles to the document
const styleElement = document.createElement('style');
styleElement.textContent = pasteOverlayStyles;
document.head.appendChild(styleElement);

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