import { useState } from 'react';
import { ShieldAlert, Calendar, Mail, CheckCircle2, ChevronRight, X } from 'lucide-react';
import { api } from '../services/api';

export default function OnboardingModal({ onClose }) {
  const [currentSlide, setCurrentSlide] = useState(0);
  const [acceptedTerms, setAcceptedTerms] = useState(false);

  const slides = [
    {
      title: "Tactical Panic Mode",
      icon: <ShieldAlert className="w-12 h-12 text-[#FF453A] animate-pulse" />,
      description: "When a deadline is closing in fast, launch Panic Mode. Answer 2-3 brief context-gathering questions, and our AI agents will immediately construct a time-boxed rescue plan.",
      color: "border-[#FF453A]/20 bg-[#FF453A]/5 text-[#FF453A]"
    },
    {
      title: "Google Calendar Sync",
      icon: <Calendar className="w-12 h-12 text-blue-400" />,
      description: "After reviewing your AI-generated subtasks, confirm the plan. The Scheduler Agent automatically packs the items consecutively and books events directly on your Google Calendar.",
      color: "border-blue-500/20 bg-blue-500/5 text-blue-400"
    },
    {
      title: "Proactive Gmail Nudges",
      icon: <Mail className="w-12 h-12 text-teal-400" />,
      description: "ZeroHour works in the background using the Gmail API. You'll receive emails and in-app alerts 24h, 6h, and 1h before your deadline to keep you accountable and focused.",
      color: "border-teal-500/20 bg-teal-500/5 text-teal-400"
    },
    {
      title: "Terms & Conditions",
      icon: <span className="material-symbols-outlined text-4xl text-emerald-400">gavel</span>,
      description: "Please accept the platform terms and authorize ZeroHour access to your Google Calendar and Gmail to continue.",
      color: "border-emerald-500/20 bg-emerald-500/5 text-emerald-400",
      isTerms: true
    }
  ];

  const handleNext = () => {
    if (currentSlide < slides.length - 1) {
      setCurrentSlide(currentSlide + 1);
    } else {
      if (acceptedTerms) {
        handleComplete();
      }
    }
  };

  const handleSkip = () => {
    setCurrentSlide(slides.length - 1);
  };

  const handleComplete = () => {
    localStorage.setItem('zerohour_onboarded', 'true');
    api.markOnboarded().catch(err => console.error('Failed to mark onboarded:', err));
    onClose();
  };

  const slide = slides[currentSlide];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-md p-4 animate-slide-up">
      <div className="glass-panel p-8 max-w-lg w-full relative border-[#FF453A]/20 flex flex-col gap-6">
        
        {/* Skip button (only shown if not on the final Terms slide) */}
        {currentSlide < slides.length - 1 && (
          <button 
            onClick={handleSkip}
            className="absolute top-4 right-4 p-2 text-gray-500 hover:text-white transition-colors"
            title="Skip to terms"
          >
            <X className="w-5 h-5" />
          </button>
        )}

        {/* Slide Icon */}
        <div className={`w-20 h-20 rounded-xl border flex items-center justify-center mx-auto mb-2 ${slide.color}`}>
          {slide.icon}
        </div>

        {/* Slide Title */}
        <div className="text-center">
          <span className="font-mono text-[9px] text-[#FF453A] uppercase tracking-[0.3em] font-bold block mb-1">
            TUTORIAL SLIDE {currentSlide + 1} OF {slides.length}
          </span>
          <h2 className="text-2xl font-black uppercase tracking-tight text-white italic">
            {slide.title}
          </h2>
        </div>

        {/* Slide Description / Terms Box */}
        {slide.isTerms ? (
          <div className="flex flex-col gap-4 max-w-sm mx-auto w-full">
            <p className="text-xs text-gray-400 text-center leading-relaxed">
              {slide.description}
            </p>
            <div className="h-32 overflow-y-auto bg-black/40 border border-white/10 p-3 rounded-lg text-[10px] text-gray-500 font-mono text-left leading-normal">
              <p className="font-bold text-gray-300 mb-1">ZeroHour Terms of Service</p>
              <p className="mb-2">ZeroHour is an emergency productivity tool designed to help you deconstruct tasks and schedule them in crisis situations. By accepting, you authorize the following operations:</p>
              <p className="font-bold text-gray-400 mb-0.5">1. Google Calendar Integration</p>
              <p className="mb-2">We will automatically insert, modify, and schedule emergency focus blocks consecutively onto your primary Google Calendar upon your confirmation of an AI-generated plan.</p>
              <p className="font-bold text-gray-400 mb-0.5">2. Gmail & Notification Nudges</p>
              <p className="mb-2">ZeroHour will send email reminders and nudges to you at 24h, 6h, and 1h intervals relative to your deadlines to enforce compliance and prevent procrastination.</p>
              <p className="font-bold text-gray-400 mb-0.5">3. AI Processing & Privacy</p>
              <p className="mb-2">Goal details, constraint notes, and uploaded documents (PDFs, images) are securely forwarded to Gemini AI models solely to synthesize your survival plan. Your data is not stored or shared for model training.</p>
            </div>
            <label className="flex items-center gap-2.5 cursor-pointer select-none text-[11px] text-gray-300 mt-1">
              <input 
                type="checkbox" 
                checked={acceptedTerms}
                onChange={(e) => setAcceptedTerms(e.target.checked)}
                className="w-4 h-4 rounded accent-[#FF453A] border-white/20 bg-black/40 text-[#FF453A]"
              />
              <span>I agree to the Terms & Conditions</span>
            </label>
          </div>
        ) : (
          <p className="text-xs text-gray-400 text-center leading-relaxed max-w-sm mx-auto min-h-[72px]">
            {slide.description}
          </p>
        )}

        {/* Progress Dots */}
        <div className="flex justify-center gap-1.5 mt-2">
          {slides.map((_, idx) => (
            <div 
              key={idx}
              className={`h-1.5 rounded-full transition-all duration-300 ${
                idx === currentSlide ? 'w-6 bg-[#FF453A]' : 'w-1.5 bg-white/10'
              }`}
            />
          ))}
        </div>

        {/* Footer Actions */}
        <div className="flex justify-between items-center mt-4 pt-4 border-t border-white/5">
          {currentSlide < slides.length - 1 ? (
            <button 
              onClick={handleSkip}
              className="text-[10px] font-mono tracking-widest text-gray-500 hover:text-white uppercase transition-colors"
            >
              Skip Intro
            </button>
          ) : (
            <div className="text-[9px] font-mono text-gray-500 uppercase tracking-wider">
              Required to proceed
            </div>
          )}
          
          <button 
            onClick={handleNext}
            disabled={currentSlide === slides.length - 1 && !acceptedTerms}
            className="btn btn-primary py-2 px-6 flex items-center gap-1 text-[10px] disabled:opacity-30 disabled:cursor-not-allowed"
          >
            {currentSlide === slides.length - 1 ? (
              <>
                <CheckCircle2 className="w-4 h-4" /> Finish
              </>
            ) : (
              <>
                Next Slide <ChevronRight className="w-4 h-4" />
              </>
            )}
          </button>
        </div>

      </div>
    </div>
  );
}
