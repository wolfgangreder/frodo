import { create } from 'zustand';

let notificationId = 0;

/**
 * UI Store - manages global UI state
 * - Sidebar open/closed state (responsive)
 * - Notification queue (stacked toasts)
 * - Loading states
 */
const useUiStore = create((set, get) => ({
  // Sidebar state
  sidebarOpen: true,
  sidebarMobileOpen: false,

  // Notification queue
  notifications: [],

  // Global loading state
  isLoading: false,
  loadingMessage: '',

  // Actions
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setSidebarOpen: (open) => set({ sidebarOpen: open }),

  toggleMobileSidebar: () => set((state) => ({ sidebarMobileOpen: !state.sidebarMobileOpen })),
  setMobileSidebarOpen: (open) => set({ sidebarMobileOpen: open }),

  // Notification actions (queue-based)
  showNotification: (message, severity = 'info', duration = 5000) =>
    set((state) => ({
      notifications: [
        ...state.notifications,
        { id: ++notificationId, message, severity, duration },
      ],
    })),

  showSuccess: (message, duration = 5000) =>
    get().showNotification(message, 'success', duration),

  showError: (message, duration = 7000) =>
    get().showNotification(message, 'error', duration),

  showWarning: (message, duration = 5000) =>
    get().showNotification(message, 'warning', duration),

  showInfo: (message, duration = 5000) =>
    get().showNotification(message, 'info', duration),

  dismissNotification: (id) =>
    set((state) => ({
      notifications: state.notifications.filter((n) => n.id !== id),
    })),

  clearNotification: () =>
    set((state) => ({
      notifications: state.notifications.slice(1),
    })),

  // Loading actions
  setLoading: (isLoading, message = '') =>
    set({ isLoading, loadingMessage: message }),

  startLoading: (message = 'Loading...') =>
    set({ isLoading: true, loadingMessage: message }),

  stopLoading: () =>
    set({ isLoading: false, loadingMessage: '' }),
}));

export default useUiStore;
