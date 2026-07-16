import React, { createContext, useContext, useState, ReactNode } from 'react';

/** Supported viewer roles for field-visibility control */
export type ViewerRole = 'STUDENT' | 'RUNNER' | 'ADMIN';

interface AuthState {
  role: ViewerRole;
  userId: number;
}

interface AuthContextType extends AuthState {
  setRole: (role: ViewerRole) => void;
  setUserId: (id: number) => void;
}

const AuthContext = createContext<AuthContextType>({
  role: 'STUDENT',
  userId: 1,
  setRole: () => {},
  setUserId: () => {},
});

export const useAuth = () => useContext(AuthContext);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [state, setState] = useState<AuthState>({ role: 'STUDENT', userId: 1 });

  const setRole = (role: ViewerRole) => setState((prev) => ({ ...prev, role }));
  const setUserId = (id: number) => setState((prev) => ({ ...prev, userId: id }));

  return (
    <AuthContext.Provider value={{ ...state, setRole, setUserId }}>
      {children}
    </AuthContext.Provider>
  );
};
