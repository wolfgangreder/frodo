/*
 * Copyright 2026 Wolfgang Reder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { create } from 'zustand';

let notificationId = 0;

/**
 * UI Store - manages global UI state
 * - Notification queue (stacked toasts)
 * - Loading states
 */
const useUiStore = create((set, get) => ({
  // Note: sidebar open/closed state is managed by PatternFly's <Page
  // isManagedSidebar> (see AppShell.jsx / Header.jsx PageToggleButton),
  // not by this store.

  // Notification queue
  notifications: [],

  // Global loading state
  isLoading: false,
  loadingMessage: '',

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
