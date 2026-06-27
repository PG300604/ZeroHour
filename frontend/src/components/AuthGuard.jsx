import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { api } from '../services/api';

export default function AuthGuard({ children }) {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    let active = true;
    const checkAuth = async () => {
      try {
        await api.getMe();
        if (active) {
          setAuthenticated(true);
          setLoading(false);
        }
      } catch (err) {
        if (active) {
          console.warn('Authentication check failed:', err);
          navigate('/');
        }
      }
    };
    checkAuth();
    return () => {
      active = false;
    };
  }, [navigate]);

  if (loading) {
    return (
      <div className="min-h-screen bg-[#0D0D0D] flex flex-col justify-center items-center gap-4">
        <Loader2 className="w-10 h-10 text-[#FF453A] animate-spin" />
        <p className="font-mono text-xs text-[#FF453A] uppercase tracking-[0.2em] animate-pulse">
          Authenticating commander...
        </p>
      </div>
    );
  }

  return authenticated ? <>{children}</> : null;
}
