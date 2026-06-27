import React from 'react';
import { useNavigate } from 'react-router-dom';

export default function Terms() {
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
        
        <h1 className="font-display font-black text-4xl uppercase italic tracking-tight mb-8">Terms of Service</h1>
        
        <div className="glass-panel p-8 border-white/10 bg-white/2 rounded-2xl space-y-6 text-sm text-gray-300 leading-relaxed font-mono">
          <p>
            <span className="text-[#FF453A] font-bold">REVISION:</span> VERSION 1.0.4 // ACTIVE
          </p>
          <p>
            Welcome to ZeroHour. By accessing our platform, you agree to these Terms of Service. Please read them carefully.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">1. Services Eligibility</h2>
          <p>
            You must log in using a valid Google OAuth Account. You agree to let the AI agents add, edit, and delete scheduling blocks in your calendar.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">2. Acceptable Use Policy</h2>
          <p>
            You agree not to exploit the multi-agent system, attempt SQL/NoSQL injection attacks on our Firebase backend, or perform automated spam requests against the Google Gemini API.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">3. Limitation of Liability</h2>
          <p>
            ZeroHour is provided "as-is" without warranty. While the Planner and Scheduler agents do their best to help you meet deadlines, ZeroHour is not responsible for missed deadlines, grades, or exam targets.
          </p>
        </div>
      </div>
      
      <footer className="text-center text-[10px] font-mono text-gray-600 mt-12 uppercase tracking-widest">
        Legals verified // ZeroHour operations
      </footer>
    </div>
  );
}
