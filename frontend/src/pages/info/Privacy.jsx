import React from 'react';
import { useNavigate } from 'react-router-dom';

export default function Privacy() {
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
        
        <h1 className="font-display font-black text-4xl uppercase italic tracking-tight mb-8">Privacy Protocol</h1>
        
        <div className="glass-panel p-8 border-white/10 bg-white/2 rounded-2xl space-y-6 text-sm text-gray-300 leading-relaxed font-mono">
          <p>
            <span className="text-[#FF453A] font-bold">CLASSIFICATION:</span> SECURE // USER DATA PROTECTION
          </p>
          <p>
            ZeroHour prioritizes data security and minimizes data collection to only what is necessary to orchestrate and execute your scheduling requests.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">1. OAuth Scope Usage</h2>
          <p>
            ZeroHour requests read/write access to your Google Calendar strictly to schedule task blocks, sync event updates, and prevent scheduling conflicts. Access is validated using secure Firebase OAuth tokens.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">2. Storage of Personal Information</h2>
          <p>
            We store basic user credentials (email, name) and details about your tasks/panic sessions in a secure Firebase Firestore database. We do not Sell, distribute, or share this data with third parties.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">3. Cookie & Cache Usage</h2>
          <p>
            We use secure browser cookies (JSESSIONID) with SameSite=None and Secure flags to maintain your login session. We store onboarding consent and layout states locally in your browser storage.
          </p>
        </div>
      </div>
      
      <footer className="text-center text-[10px] font-mono text-gray-600 mt-12 uppercase tracking-widest">
        Data integrity verified // ZeroHour Security Command
      </footer>
    </div>
  );
}
