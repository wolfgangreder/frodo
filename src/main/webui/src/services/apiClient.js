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

import axios from 'axios';

// Derive API base URL from Vite's configured base path.
// In production (base='/frodo/'): BASE_URL='/frodo/' → baseURL='/frodo/api'
// In dev mode the same base applies, keeping API calls consistent.
const _base = (import.meta.env.BASE_URL || '/').replace(/\/$/, '');

// Create axios instance with default configuration
const apiClient = axios.create({
  baseURL: `${_base}/api`,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor for logging and adding common headers
apiClient.interceptors.request.use(
  (config) => {
    // Add timestamp for debugging
    config.metadata = { startTime: new Date() };
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor for error handling
apiClient.interceptors.response.use(
  (response) => {
    // Calculate request duration for debugging
    const duration = new Date() - response.config.metadata.startTime;
    if (import.meta.env.DEV) {
      console.debug(`API ${response.config.method?.toUpperCase()} ${response.config.url} - ${duration}ms`);
    }
    return response;
  },
  (error) => {
    // Transform error for consistent handling
    const customError = {
      message: error.response?.data?.message || error.message || 'An unexpected error occurred',
      status: error.response?.status,
      statusText: error.response?.statusText,
      data: error.response?.data,
      originalError: error,
    };

    // Log errors in development
    if (import.meta.env.DEV) {
      console.error('API Error:', customError);
    }

    return Promise.reject(customError);
  }
);

export default apiClient;
