import axios from 'axios';

// Create axios instance with default configuration
const apiClient = axios.create({
  baseURL: '/api',
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
    if (process.env.NODE_ENV === 'development') {
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
    if (process.env.NODE_ENV === 'development') {
      console.error('API Error:', customError);
    }

    return Promise.reject(customError);
  }
);

export default apiClient;
