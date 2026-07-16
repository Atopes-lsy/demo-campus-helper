import React, { createContext, useContext, useState, ReactNode } from 'react';

export type ViewerRole = 'STUDENT' | 'RUNNER' | 'ADMIN';

interface AuthState {
  role: ViewerRole;
  userId: number;
  isLoggedIn: boolean;
  userName: string;
}

interface AuthContextType extends AuthState {
  setRole: (role: ViewerRole) => void;
  setUserId: (id: number) => void;
  login: (role: ViewerRole, userId: number, userName: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType>({
  role: 'STUDENT',
  userId: 1,
  isLoggedIn: false,
  userName: '',
  setRole: () => {},
  setUserId: () => {},
  login: () => {},
  logout: () => {},
});

export const useAuth = () => useContext(AuthContext);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [state, setState] = useState<AuthState>({
    role: 'STUDENT',
    userId: 1,
    isLoggedIn: false,
    userName: '',
  });

  const setRole = (role: ViewerRole) => setState((prev) => ({ ...prev, role }));
  const setUserId = (id: number) => setState((prev) => ({ ...prev, userId: id }));

  const login = (role: ViewerRole, userId: number, userName: string) => {
    setState({ role, userId, isLoggedIn: true, userName });
  };

  const logout = () => {
    setState({ role: 'STUDENT', userId: 1, isLoggedIn: false, userName: '' });
  };

  return (
    <AuthContext.Provider value={{ ...state, setRole, setUserId, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
