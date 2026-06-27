const API_BASE = import.meta.env.VITE_API_URL || (window.location.hostname === 'localhost' ? 'http://localhost:8080' : '');

function getCookie(name) {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) return parts.pop().split(';').shift();
  return null;
}

async function request(path, options = {}) {
  const url = `${API_BASE}${path}`;
  
  // Ensure credentials (cookies) are passed for Spring Security sessions
  options.credentials = 'include';
  
  if (options.body && typeof options.body === 'object') {
    options.body = JSON.stringify(options.body);
    options.headers = {
      ...options.headers,
      'Content-Type': 'application/json',
    };
  }

  // Set CSRF token header for mutating requests
  const method = options.method || 'GET';
  const isSafeMethod = /^(GET|HEAD|OPTIONS|TRACE)$/i.test(method);
  if (!isSafeMethod) {
    const csrfToken = getCookie('XSRF-TOKEN');
    if (csrfToken) {
      options.headers = {
        ...options.headers,
        'X-XSRF-TOKEN': csrfToken,
      };
    }
  }

  const response = await fetch(url, options);

  if (response.status === 401 || response.status === 410) {
    // Session expired or unauthorized
    if (window.location.pathname !== '/') {
      window.location.href = '/';
    }
    throw new Error('Unauthorized');
  }

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || `HTTP error! status: ${response.status}`);
  }

  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return await response.json();
  }
  return await response.text();
}

export const api = {
  // Generic request methods
  get: (path) => request(path, { method: 'GET' }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  post: (path, body) => request(path, { method: 'POST', body }),
  delete: (path) => request(path, { method: 'DELETE' }),

  // Auth
  getMe: () => request('/api/auth/me'),
  logout: () => request('/api/auth/logout', { method: 'POST' }),
  loginUrl: `${API_BASE}/oauth2/authorization/google`,

  // Tasks
  getTasks: () => request('/api/tasks'),
  createTask: (task) => request('/api/tasks', { method: 'POST', body: task }),
  getTask: (id) => request(`/api/tasks/${id}`),
  updateTask: (id, task) => request(`/api/tasks/${id}`, { method: 'PUT', body: task }),
  deleteTask: (id) => request(`/api/tasks/${id}`, { method: 'DELETE' }),
  confirmTask: (id) => request(`/api/tasks/${id}/confirm`, { method: 'POST' }),
  reprioritizeTask: (id) => request(`/api/tasks/${id}/reprioritize`, { method: 'POST' }),
  getSubtasks: (id) => request(`/api/tasks/${id}/subtasks`),
  toggleSubtask: (taskId, subtaskId, status) => 
    request(`/api/tasks/${taskId}/subtasks/${subtaskId}`, { 
      method: 'PUT', 
      body: { status } 
    }),

  // Panic Mode
  startPanic: (message, attachment) => request('/api/panic/start', { method: 'POST', body: { message, attachment } }),
  replyPanic: (id, message, attachment) => request(`/api/panic/${id}/reply`, { method: 'POST', body: { message, attachment } }),
  editPanicPlan: (id, subtasks) => request(`/api/panic/${id}/edit`, { method: 'POST', body: subtasks }),
  confirmPanicPlan: (id) => request(`/api/panic/${id}/confirm`, { method: 'POST' }),
  getPanicSession: (id) => request(`/api/panic/${id}`),
  getPanicConversation: (id) => request(`/api/panic/${id}/conversation`),
  getPanicSessions: () => request('/api/panic'),
  deletePanicSession: (id) => request(`/api/panic/${id}`, { method: 'DELETE' }),
  renamePanicSession: (id, title) => request(`/api/panic/${id}/rename`, { method: 'PUT', body: { title } }),

  // Notifications
  getNotifications: () => request('/api/notifications'),
  markNotificationRead: (id) => request(`/api/notifications/${id}/read`, { method: 'PUT' }),

  // SSE Stream URL
  getStreamUrl: (sessionId) => `${API_BASE}/api/agents/stream/${sessionId}`,
};

// Named exports for Settings
export const getSettings = () => api.get('/api/settings');
export const updatePreferences = (prefs) => api.put('/api/settings/preferences', prefs);
export const markOnboarded = () => api.put('/api/settings/onboarded');

// Named exports for Notifications
export const getNotifications = () => api.get('/api/notifications');
export const getUnreadCount = () => api.get('/api/notifications/unread-count');
export const markRead = (id) => api.put(`/api/notifications/${id}/read`);
export const markAllRead = () => api.put('/api/notifications/read-all');

