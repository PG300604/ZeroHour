import React from 'react';
import { useNavigate } from 'react-router-dom';

export default function Security() {
  const navigate = useNavigate();
  return (
    <div className="min-h-screen bg-[#0D0D0D] text-white p-6 md:p-12 font-sans flex flex-col justify-between">
      <div className="max-w-3xl mx-auto w-full">
        <button 
          onClick={() => navigate('/')} 
          className="flex items-center gap-2 text-gray-400 hover:text-white font-mono text-xs uppercase tracking-widest mb-10 transition-colors bg-transparent border-0 cursor-pointer p-0"
        >
          <span className="material-symbols-outlined text-sm">arrow_back</span> Back to Mission Control
        </button>
        
        <h1 className="font-display font-black text-4xl uppercase italic tracking-tight mb-8">Security Architecture</h1>
        
        <div className="glass-panel p-8 border-white/10 bg-white/2 rounded-2xl space-y-6 text-sm text-gray-300 leading-relaxed font-mono">
          <p>
            <span className="text-[#FF453A] font-bold">PROTOCOL:</span> HARDENED // SYSTEM INTEGRITY
          </p>
          <p>
            ZeroHour integrates enterprise-grade web application security controls to guarantee data isolation and protect against malicious vectors.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">1. OAuth 2.0 Identity Management</h2>
          <p>
            Authentication is brokered securely through Google OAuth 2.0. We never see or store your raw Google password. All API tokens are kept inside backend session stores, isolated from the browser window.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">2. IDOR Prevention & DB Isolation</h2>
          <p>
            All Firestore queries are parametrized with the authenticated `userId` retrieved from the Spring Security Principal. This prevents Insecure Direct Object Reference (IDOR) attacks, ensuring users can never view, update, or delete other users' tasks.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">3. CSRF Protection & Cookie Hardening</h2>
          <p>
            Spring Security enforces Cross-Site Request Forgery (CSRF) token checks on all mutating (POST/PUT/DELETE) requests. Session cookies use `SameSite=None` and `Secure` attributes to prevent cross-site request leakage.
          </p>
        </div>
      </div>
      
      <footer className="text-center text-[10px] font-mono text-gray-600 mt-12 uppercase tracking-widest">
        Security audited // ZeroHour Ops
      </footer>
    </div>
  );
}
