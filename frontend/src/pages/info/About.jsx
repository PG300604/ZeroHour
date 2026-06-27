import React from 'react';
import { useNavigate } from 'react-router-dom';

export default function About() {
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
        
        <h1 className="font-display font-black text-4xl uppercase italic tracking-tight mb-8">About ZeroHour</h1>
        
        <div className="glass-panel p-8 border-white/10 bg-white/2 rounded-2xl space-y-6 text-sm text-gray-300 leading-relaxed font-mono">
          <p>
            <span className="text-[#FF453A] font-bold">PROJECT CODE:</span> ZH_INIT_882
          </p>
          <p>
            ZeroHour is an advanced AI-driven task orchestration system designed for high-stress scenarios. When deadlines approach and panic sets in, ZeroHour deploys a coordinate group of autonomous agents to analyze, prioritize, structure, and schedule your work.
          </p>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">The Agent Core</h2>
          <ul className="list-disc pl-5 space-y-2">
            <li><span className="text-[#14B8A6] font-bold">PlannerAgent</span>: Breaks down complex user requests and document attachments (PDFs, images) into distinct tactical milestones.</li>
            <li><span className="text-amber-500 font-bold">PrioritizerAgent</span>: Gauges task dependency paths and urgency weights.</li>
            <li><span className="text-blue-400 font-bold">SchedulerAgent</span>: Dynamically places tasks into your Google Calendar, respecting custom focus windows and inserting 10-minute breaks.</li>
            <li><span className="text-purple-400 font-bold">NudgeAgent</span>: Delivers Gmail reminders and alerts before task deadlines.</li>
          </ul>
          
          <h2 className="text-white font-bold uppercase tracking-wider text-base pt-4 border-t border-white/5">Hackathon Build</h2>
          <p>
            ZeroHour was built as an agentic pair-programming showcase. The backend runs on Java Spring Boot and integrates with Firebase Firestore and Google APIs, while the frontend is constructed using React and hosted via Google Firebase.
          </p>
        </div>
      </div>
      
      <footer className="text-center text-[10px] font-mono text-gray-600 mt-12 uppercase tracking-widest">
        Command your time. Execute your mission.
      </footer>
    </div>
  );
}
