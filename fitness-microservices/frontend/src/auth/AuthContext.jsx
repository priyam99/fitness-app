import { createContext, useContext, useState } from "react";
import { login as keycloakLogin, decodeToken } from "./keycloak";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem("access_token"));
  const [userId, setUserId] = useState(() => localStorage.getItem("user_id"));

  async function login(email, password) {
    const data = await keycloakLogin(email, password);
    const claims = decodeToken(data.access_token);

    localStorage.setItem("access_token", data.access_token);
    localStorage.setItem("refresh_token", data.refresh_token);
    localStorage.setItem("user_id", claims.sub);

    setToken(data.access_token);
    setUserId(claims.sub);
  }

  function logout() {
    localStorage.removeItem("access_token");
    localStorage.removeItem("refresh_token");
    localStorage.removeItem("user_id");
    setToken(null);
    setUserId(null);
  }

  return (
    <AuthContext.Provider value={{ token, userId, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
