import { create } from 'zustand';

/**
 * UI Store - manages global UI state
 * - Sidebar open/closed state (responsive)
 * - Current page/navigation
 * - Notifications/snackbar
 * - Loading states
 */
const useUiStore = create((set, get) => ({
  // Sidebar state
  sidebarOpen: true,
  sidebarMobileOpen: false,

  // Notifications (snackbar)
  notification: null, // { message, severity: 'success' | 'error' | 'warning' | 'info', duration }

  // Global loading state
  isLoading: false,
  loadingMessage: '',

  // Actions
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  setSidebarOpen: (open) => set({ sidebarOpen: open }),

  toggleMobileSidebar: () => set((state) => ({ sidebarMobileOpen: !state.sidebarMobileOpen })),
  setMobileSidebarOpen: (open) => set({ sidebarMobileOpen: open }),

  // Notification actions
  showNotification: (message, severity = 'info', duration = 5000) =>
    set({ notification: { message, severity, duration, key: Date.now() } }),

  showSuccess: (message, duration = 5000) =>
    get().showNotification(message, 'success', duration),

  showError: (message, duration = 7000) =>
    get().showNotification(message, 'error', duration),

  showWarning: (message, duration = 5000) =>
    get().showNotification(message, 'warning', duration),

  showInfo: (message, duration = 5000) =>
    get().showNotification(message, 'info', duration),

  clearNotification: () => set({ notification: null }),

  // Loading actions
  setLoading: (isLoading, message = '') =>
    set({ isLoading, loadingMessage: message }),

  startLoading: (message = 'Loading...') =>
    set({ isLoading: true, loadingMessage: message }),

  stopLoading: () =>
    set({ isLoading: false, loadingMessage: '' }),
}));

export default useUiStore;
