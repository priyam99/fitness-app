import axios from "axios";

const KEYCLOAK_BASE_URL = "http://localhost:8180";
const REALM = "fitness-app";
const CLIENT_ID = "fitness-app-client";
const CLIENT_SECRET = import.meta.env.VITE_KEYCLOAK_CLIENT_SECRET;

export async function login(email, password) {
  const params = new URLSearchParams();
  params.append("grant_type", "password");
  params.append("client_id", CLIENT_ID);
  params.append("client_secret", CLIENT_SECRET);
  params.append("username", email);
  params.append("password", password);

  const response = await axios.post(
    `${KEYCLOAK_BASE_URL}/realms/${REALM}/protocol/openid-connect/token`,
    params,
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  );

  return response.data;
}

export async function refreshAccessToken(refreshToken) {
  const params = new URLSearchParams();
  params.append("grant_type", "refresh_token");
  params.append("client_id", CLIENT_ID);
  params.append("client_secret", CLIENT_SECRET);
  params.append("refresh_token", refreshToken);

  const response = await axios.post(
    `${KEYCLOAK_BASE_URL}/realms/${REALM}/protocol/openid-connect/token`,
    params,
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  );

  return response.data;
}

export function decodeToken(token) {
  const payload = token.split(".")[1];
  const decoded = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
  return JSON.parse(decoded);
}
