/**
 * PDF Chatbot Frontend Application
 * Handles document upload, management, and chat interactions
 */

// ===== State Management =====
const state = {
    documents: [],
    selectedDocumentId: null,
    isUploading: false,
    isSending: false,
    pollingIntervals: new Map()
};

// ===== API Service =====
const api = {
    baseUrl: '/api',
    
    async uploadDocument(file) {
        const formData = new FormData();
        formData.append('file', file);
        
        const response = await fetch(`${this.baseUrl}/documents/upload`, {
            method: 'POST',
            body: formData
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Upload failed');
        }
        
        return response.json();
    },
    
    async getDocuments() {
        const response = await fetch(`${this.baseUrl}/documents`);
        
        if (!response.ok) {
            throw new Error('Failed to fetch documents');
        }
        
        return response.json();
    },
    
    async getDocumentStatus(documentId) {
        const response = await fetch(`${this.baseUrl}/documents/${documentId}/status`);
        
        if (!response.ok) {
            throw new Error('Failed to fetch document status');
        }
        
        return response.json();
    },
    
    async deleteDocument(documentId) {
        const response = await fetch(`${this.baseUrl}/documents/${documentId}`, {
            method: 'DELETE'
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Delete failed');
        }
        
        return response.json();
    },
    
    async chat(documentId, question, responseFormat = 'TEXT') {
        const response = await fetch(`${this.baseUrl}/chat`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                documentId,
                question,
                responseFormat
            })
        });
        
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Chat request failed');
        }
        
        return response.json();
    }
};

// ===== DOM Elements =====
const elements = {
    // Upload
    dropzone: document.getElementById('dropzone'),
    fileInput: document.getElementById('file-input'),
    uploadProgress: document.getElementById('upload-progress'),
    progressFill: document.getElementById('progress-fill'),
    progressText: document.getElementById('progress-text'),
    
    // Documents
    documentsList: document.getElementById('documents-list'),
    documentsEmpty: document.getElementById('documents-empty'),
    
    // Views
    noDocumentView: document.getElementById('no-document-view'),
    chatView: document.getElementById('chat-view'),
    
    // Chat Header
    chatDocumentName: document.getElementById('chat-document-name'),
    chatDocumentMeta: document.getElementById('chat-document-meta'),
    jsonFormatToggle: document.getElementById('json-format-toggle'),
    
    // Messages
    messagesContainer: document.getElementById('messages-container'),
    welcomeMessage: document.getElementById('welcome-message'),
    
    // Input
    chatForm: document.getElementById('chat-form'),
    questionInput: document.getElementById('question-input'),
    sendButton: document.getElementById('send-button'),
    
    // Toast
    toastContainer: document.getElementById('toast-container'),
    
    // Modal
    deleteModal: document.getElementById('delete-modal'),
    cancelDelete: document.getElementById('cancel-delete'),
    confirmDelete: document.getElementById('confirm-delete')
};

// ===== Toast Notifications =====
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    
    elements.toastContainer.appendChild(toast);
    
    setTimeout(() => {
        toast.classList.add('removing');
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

// ===== Document Management =====
async function loadDocuments() {
    try {
        const response = await api.getDocuments();
        state.documents = response.documents || [];
        renderDocumentsList();
        
        // Start polling for processing documents
        state.documents.forEach(doc => {
            if (doc.status === 'PROCESSING' || doc.status === 'UPLOADING') {
                startPollingStatus(doc.documentId);
            }
        });
    } catch (error) {
        console.error('Failed to load documents:', error);
        showToast('Failed to load documents', 'error');
    }
}

function renderDocumentsList() {
    if (state.documents.length === 0) {
        elements.documentsEmpty.classList.remove('hidden');
        elements.documentsList.innerHTML = '';
        elements.documentsList.appendChild(elements.documentsEmpty);
        return;
    }
    
    elements.documentsEmpty.classList.add('hidden');
    
    const html = state.documents.map(doc => `
        <div class="document-item ${doc.documentId === state.selectedDocumentId ? 'selected' : ''}" 
             data-document-id="${doc.documentId}">
            <div class="document-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                    <polyline points="14 2 14 8 20 8"></polyline>
                </svg>
            </div>
            <div class="document-info">
                <div class="document-name" title="${escapeHtml(doc.filename)}">${escapeHtml(doc.filename)}</div>
                <div class="document-meta">
                    <span class="document-status ${doc.status.toLowerCase()}">${formatStatus(doc.status)}</span>
                    ${doc.pageCount ? `<span>${doc.pageCount} pages</span>` : ''}
                </div>
            </div>
            <div class="document-actions">
                <button class="delete-btn" data-document-id="${doc.documentId}" title="Delete document">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="3 6 5 6 21 6"></polyline>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                    </svg>
                </button>
            </div>
        </div>
    `).join('');
    
    elements.documentsList.innerHTML = html;
    
    // Re-add event listeners
    elements.documentsList.querySelectorAll('.document-item').forEach(item => {
        item.addEventListener('click', (e) => {
            if (!e.target.closest('.delete-btn')) {
                selectDocument(item.dataset.documentId);
            }
        });
    });
    
    elements.documentsList.querySelectorAll('.delete-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            showDeleteModal(btn.dataset.documentId);
        });
    });
}

function formatStatus(status) {
    switch (status) {
        case 'READY': return 'Ready';
        case 'PROCESSING': return 'Processing';
        case 'UPLOADING': return 'Uploading';
        case 'FAILED': return 'Failed';
        default: return status;
    }
}

function selectDocument(documentId) {
    const doc = state.documents.find(d => d.documentId === documentId);
    if (!doc) return;
    
    if (doc.status !== 'READY') {
        showToast('Document is still processing. Please wait.', 'warning');
        return;
    }
    
    state.selectedDocumentId = documentId;
    renderDocumentsList();
    showChatView(doc);
}

function showChatView(doc) {
    elements.noDocumentView.classList.add('hidden');
    elements.chatView.classList.remove('hidden');
    
    elements.chatDocumentName.textContent = doc.filename;
    elements.chatDocumentMeta.textContent = `${doc.pageCount || 0} pages`;
    
    // Clear messages and show welcome
    elements.messagesContainer.innerHTML = '';
    elements.messagesContainer.appendChild(elements.welcomeMessage.cloneNode(true));
    elements.welcomeMessage.classList.remove('hidden');
    
    // Re-attach suggested prompt listeners
    elements.messagesContainer.querySelectorAll('.suggested-prompt').forEach(btn => {
        btn.addEventListener('click', () => {
            elements.questionInput.value = btn.dataset.prompt;
            elements.questionInput.focus();
        });
    });
}

// ===== Status Polling =====
function startPollingStatus(documentId) {
    if (state.pollingIntervals.has(documentId)) return;
    
    const intervalId = setInterval(async () => {
        try {
            const status = await api.getDocumentStatus(documentId);
            updateDocumentStatus(documentId, status);
            
            if (status.status === 'READY' || status.status === 'FAILED') {
                stopPollingStatus(documentId);
                
                if (status.status === 'READY') {
                    showToast(`"${status.filename}" is ready!`, 'success');
                } else {
                    showToast(`Processing failed: ${status.error || 'Unknown error'}`, 'error');
                }
            }
        } catch (error) {
            console.error('Polling error:', error);
        }
    }, 2000);
    
    state.pollingIntervals.set(documentId, intervalId);
}

function stopPollingStatus(documentId) {
    const intervalId = state.pollingIntervals.get(documentId);
    if (intervalId) {
        clearInterval(intervalId);
        state.pollingIntervals.delete(documentId);
    }
}

function updateDocumentStatus(documentId, status) {
    const doc = state.documents.find(d => d.documentId === documentId);
    if (doc) {
        doc.status = status.status;
        doc.pageCount = status.pageCount;
        doc.chunkCount = status.chunkCount;
        renderDocumentsList();
    }
}

// ===== File Upload =====
function setupUpload() {
    // Click to browse
    elements.dropzone.addEventListener('click', () => {
        elements.fileInput.click();
    });
    
    // File input change
    elements.fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) handleFileUpload(file);
        e.target.value = '';
    });
    
    // Drag and drop
    elements.dropzone.addEventListener('dragover', (e) => {
        e.preventDefault();
        elements.dropzone.classList.add('dragover');
    });
    
    elements.dropzone.addEventListener('dragleave', () => {
        elements.dropzone.classList.remove('dragover');
    });
    
    elements.dropzone.addEventListener('drop', (e) => {
        e.preventDefault();
        elements.dropzone.classList.remove('dragover');
        
        const file = e.dataTransfer.files[0];
        if (file) handleFileUpload(file);
    });
}

async function handleFileUpload(file) {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
        showToast('Please upload a PDF file', 'error');
        return;
    }
    
    if (file.size > 50 * 1024 * 1024) {
        showToast('File size must be less than 50MB', 'error');
        return;
    }
    
    state.isUploading = true;
    elements.uploadProgress.classList.remove('hidden');
    elements.progressFill.style.width = '0%';
    elements.progressText.textContent = 'Uploading...';
    
    // Simulate upload progress
    let progress = 0;
    const progressInterval = setInterval(() => {
        progress += Math.random() * 15;
        if (progress > 90) progress = 90;
        elements.progressFill.style.width = `${progress}%`;
    }, 200);
    
    try {
        const result = await api.uploadDocument(file);
        
        clearInterval(progressInterval);
        elements.progressFill.style.width = '100%';
        elements.progressText.textContent = 'Processing...';
        
        showToast('Document uploaded successfully!', 'success');
        
        // Add to documents list
        state.documents.unshift({
            documentId: result.documentId,
            filename: result.filename,
            status: result.status,
            pageCount: 0,
            uploadDate: result.uploadDate
        });
        
        renderDocumentsList();
        startPollingStatus(result.documentId);
        
        // Hide progress after a delay
        setTimeout(() => {
            elements.uploadProgress.classList.add('hidden');
        }, 1000);
        
    } catch (error) {
        clearInterval(progressInterval);
        elements.uploadProgress.classList.add('hidden');
        showToast(error.message || 'Upload failed', 'error');
    } finally {
        state.isUploading = false;
    }
}

// ===== Delete Modal =====
let documentToDelete = null;

function showDeleteModal(documentId) {
    documentToDelete = documentId;
    elements.deleteModal.classList.remove('hidden');
}

function hideDeleteModal() {
    documentToDelete = null;
    elements.deleteModal.classList.add('hidden');
}

async function confirmDelete() {
    if (!documentToDelete) return;
    
    try {
        await api.deleteDocument(documentToDelete);
        
        // Stop polling if active
        stopPollingStatus(documentToDelete);
        
        // Remove from list
        state.documents = state.documents.filter(d => d.documentId !== documentToDelete);
        
        // Clear selection if deleted document was selected
        if (state.selectedDocumentId === documentToDelete) {
            state.selectedDocumentId = null;
            elements.chatView.classList.add('hidden');
            elements.noDocumentView.classList.remove('hidden');
        }
        
        renderDocumentsList();
        showToast('Document deleted successfully', 'success');
    } catch (error) {
        showToast(error.message || 'Delete failed', 'error');
    } finally {
        hideDeleteModal();
    }
}

// ===== Chat Functionality =====
function setupChat() {
    elements.chatForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        const question = elements.questionInput.value.trim();
        if (!question || state.isSending || !state.selectedDocumentId) return;
        
        await sendMessage(question);
    });
    
    // Auto-resize textarea
    elements.questionInput.addEventListener('input', () => {
        elements.questionInput.style.height = 'auto';
        elements.questionInput.style.height = Math.min(elements.questionInput.scrollHeight, 150) + 'px';
    });
    
    // Enter to send (Shift+Enter for new line)
    elements.questionInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            elements.chatForm.dispatchEvent(new Event('submit'));
        }
    });
}

async function sendMessage(question) {
    state.isSending = true;
    elements.sendButton.disabled = true;
    
    // Hide welcome message
    const welcome = elements.messagesContainer.querySelector('.welcome-message');
    if (welcome) welcome.classList.add('hidden');
    
    // Add user message
    addMessage(question, 'user');
    
    // Clear input
    elements.questionInput.value = '';
    elements.questionInput.style.height = 'auto';
    
    // Add loading message
    const loadingId = addLoadingMessage();
    
    try {
        const responseFormat = elements.jsonFormatToggle.checked ? 'JSON' : 'TEXT';
        const response = await api.chat(state.selectedDocumentId, question, responseFormat);
        
        removeMessage(loadingId);
        addMessage(response.answer, 'assistant', response.sources);
        
    } catch (error) {
        removeMessage(loadingId);
        addMessage(`Sorry, I encountered an error: ${error.message}`, 'assistant');
        showToast('Failed to get response', 'error');
    } finally {
        state.isSending = false;
        elements.sendButton.disabled = false;
        elements.questionInput.focus();
    }
}

function addMessage(content, role, sources = null) {
    const messageId = 'msg-' + Date.now();
    const message = document.createElement('div');
    message.className = `message ${role}`;
    message.id = messageId;
    
    let sourcesHtml = '';
    if (sources && sources.length > 0) {
        sourcesHtml = `
            <div class="message-sources">
                <div class="sources-title">Sources</div>
                ${sources.map(source => `
                    <div class="source-item">
                        <div class="source-header">
                            <span class="source-page">Page ${source.pageNumber || 'N/A'}</span>
                            <span class="source-score">${source.relevanceScore ? (source.relevanceScore * 100).toFixed(1) + '% match' : ''}</span>
                        </div>
                        <div class="source-content">${escapeHtml(source.content || '')}</div>
                    </div>
                `).join('')}
            </div>
        `;
    }
    
    message.innerHTML = `
        <div class="message-content">
            ${escapeHtml(content)}
            ${sourcesHtml}
        </div>
    `;
    
    elements.messagesContainer.appendChild(message);
    scrollToBottom();
    
    return messageId;
}

function addLoadingMessage() {
    const messageId = 'loading-' + Date.now();
    const message = document.createElement('div');
    message.className = 'message assistant loading';
    message.id = messageId;
    message.innerHTML = `
        <div class="message-content">
            <div class="typing-indicator">
                <span></span>
                <span></span>
                <span></span>
            </div>
            Thinking...
        </div>
    `;
    
    elements.messagesContainer.appendChild(message);
    scrollToBottom();
    
    return messageId;
}

function removeMessage(messageId) {
    const message = document.getElementById(messageId);
    if (message) message.remove();
}

function scrollToBottom() {
    elements.messagesContainer.scrollTop = elements.messagesContainer.scrollHeight;
}

// ===== Utility Functions =====
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ===== Initialization =====
function init() {
    setupUpload();
    setupChat();
    loadDocuments();
    
    // Modal event listeners
    elements.cancelDelete.addEventListener('click', hideDeleteModal);
    elements.confirmDelete.addEventListener('click', confirmDelete);
    elements.deleteModal.addEventListener('click', (e) => {
        if (e.target === elements.deleteModal) hideDeleteModal();
    });
    
    // Keyboard shortcut to close modal
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !elements.deleteModal.classList.contains('hidden')) {
            hideDeleteModal();
        }
    });
}

// Start the application
document.addEventListener('DOMContentLoaded', init);
