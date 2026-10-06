import axios from "axios";
import { refreshAccessToken } from "../auth/keycloak";

const apiClient = axios.create({
  baseURL: "http://localhost:8080",
});

apiClient.interceptors.request.use((config) => {
  if (config.url === "/api/users/register") {
    return config;
  }
  const token = localStorage.getItem("access_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshPromise = null;

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const is401 = error.response?.status === 401;
    const isRegister = originalRequest.url === "/api/users/register";

    if (!is401 || isRegister || originalRequest._retried) {
      return Promise.reject(error);
    }

    const storedRefreshToken = localStorage.getItem("refresh_token");
    if (!storedRefreshToken) {
      return Promise.reject(error);
    }

    originalRequest._retried = true;

    try {
      if (!refreshPromise) {
        refreshPromise = refreshAccessToken(storedRefreshToken).finally(() => {
          refreshPromise = null;
        });
      }
      const data = await refreshPromise;

      localStorage.setItem("access_token", data.access_token);
      localStorage.setItem("refresh_token", data.refresh_token);

      originalRequest.headers.Authorization = `Bearer ${data.access_token}`;
      return apiClient(originalRequest);
    } catch (refreshError) {
      localStorage.removeItem("access_token");
      localStorage.removeItem("refresh_token");
      localStorage.removeItem("user_id");
      window.location.href = "/login";
      return Promise.reject(refreshError);
    }
  }
);

export default apiClient;
