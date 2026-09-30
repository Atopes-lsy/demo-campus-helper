import React, { createContext, useContext, useState, ReactNode } from 'react';

export type ViewerRole = 'STUDENT' | 'RUNNER' | 'ADMIN';

interface AuthState {
  role: ViewerRole;
  userId: number;
  isLoggedIn: boolean;
  userName: string;
  token: string;
  expiresAt: number;
}

interface AuthContextType extends AuthState {
  setRole: (role: ViewerRole) => void;
  setUserId: (id: number) => void;
  login: (role: ViewerRole, userId: number, userName: string, token: string, expiresAt: number) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({
  role: 'STUDENT',
  userId: 1,
  isLoggedIn: false,
  userName: '',
  token: '',
  expiresAt: 0,
  setRole: () => {},
  setUserId: () => {},
  login: () => {},
  logout: () => {},
});

export const useAuth = () => useContext(AuthContext);

export const AUTH_STORAGE_KEY = 'campus_helper_auth';

const EMPTY_AUTH: AuthState = {
  role: 'STUDENT', userId: 0, isLoggedIn: false, userName: '', token: '', expiresAt: 0,
};

function loadStoredAuth(): AuthState {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return EMPTY_AUTH;
    const stored = JSON.parse(raw) as AuthState;
    if (!stored.token || stored.expiresAt <= Date.now() / 1000) {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      return EMPTY_AUTH;
    }
    return { ...stored, isLoggedIn: true };
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return EMPTY_AUTH;
  }
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [state, setState] = useState<AuthState>(loadStoredAuth);

  const setRole = (role: ViewerRole) => setState((prev) => ({ ...prev, role }));
  const setUserId = (id: number) => setState((prev) => ({ ...prev, userId: id }));

  const login = (role: ViewerRole, userId: number, userName: string, token: string, expiresAt: number) => {
    const next = { role, userId, isLoggedIn: true, userName, token, expiresAt };
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(next));
    setState(next);
  };

  const logout = () => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    setState(EMPTY_AUTH);
  };

  return (
    <AuthContext.Provider value={{ ...state, setRole, setUserId, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
