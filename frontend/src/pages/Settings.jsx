import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { api } from '../services/api';

export default function Settings() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [preferences, setPreferences] = useState({
    emailNudges: true,
    inAppNudges: true,
    nudge24h: true,
    nudge6h: true,
    nudge1h: true,
    timezone: 'Asia/Kolkata',
  });
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    document.title = 'ZeroHour — Settings';
  }, []);

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const res = await api.get('/api/settings');
        setUser({ displayName: res.displayName, email: res.email });
        if (res.preferences) {
          setPreferences(res.preferences);
        }
      } catch (e) {
        console.error('Settings load failed:', e);
        navigate('/');
      }
    };
    fetchSettings();
  }, [navigate]);

  const handleSave = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      await api.put('/api/settings/preferences', preferences);
      setSaved(true);
      setTimeout(() => setSaved(false), 2000); // reset after 2s
    } catch (err) {
      console.error('Save failed:', err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="dashboard-layout bg-[#0D0D0D] animate-fadeIn">
      
      {/* Side Navigation Bar */}
      <nav className="hidden md:flex w-64 flex-shrink-0 h-screen sticky top-0 flex flex-col py-6 glass-panel z-40 border-r border-white/10 rounded-r-2xl">
        <div className="px-6 mb-8">
          <h1 className="font-display text-3xl font-black text-white tracking-tighter italic">ZEROHOUR</h1>
          <p className="font-mono text-[9px] text-[#FF453A] uppercase tracking-[0.3em] mt-1 opacity-80">COMMANDER_TERMINAL</p>
        </div>

        <div className="flex flex-col gap-1 flex-grow">
          <Link to="/dashboard" className="flex items-center gap-4 text-[#94a3b8] px-6 py-3 hover:bg-white/5 hover:text-white transition-all font-mono text-xs tracking-wider">
            <span className="material-symbols-outlined">dashboard</span>
            DASHBOARD
          </Link>
          <a className="flex items-center gap-4 text-[#94a3b8] px-6 py-3 hover:bg-white/5 hover:text-white transition-all font-mono text-xs tracking-wider" href="https://calendar.google.com" target="_blank" rel="noreferrer">
            <span className="material-symbols-outlined">calendar_today</span>
            CALENDAR
          </a>
          <Link to="/settings" className="flex items-center gap-4 text-white bg-white/5 border-l-4 border-[#FF453A] px-6 py-3 transition-all font-mono text-xs tracking-wider font-bold">
            <span className="material-symbols-outlined text-[#FF453A]">settings</span>
            SETTINGS
          </Link>
        </div>

        <div className="px-6 mt-auto pb-8">
          <Link 
            to="/dashboard" 
            className="w-full bg-white/5 text-[#94a3b8] hover:text-white py-4 rounded-xl font-black font-mono text-xs flex items-center justify-center gap-2 hover:bg-white/10 transition-all border border-white/10 uppercase tracking-widest text-center"
          >
            <span className="material-symbols-outlined text-sm">arrow_back</span>
            Back to core
          </Link>
        </div>
      </nav>

      {/* Main Canvas */}
      <main className="flex-grow min-h-screen flex flex-col relative bg-[#0D0D0D]">
        
        {/* Header */}
        <header className="h-16 flex justify-between items-center px-6 md:px-12 border-b border-white/10 glass-panel z-30 sticky top-0">
          <div className="flex items-center gap-3">
            <Link to="/dashboard" className="p-2 rounded-lg hover:bg-white/5 text-[#94a3b8] hover:text-white transition-all mr-2">
              <span className="material-symbols-outlined text-xl">arrow_back</span>
            </Link>
            <span className="font-mono text-xs text-[#94a3b8] uppercase tracking-widest">Preferences</span>
          </div>
        </header>

        {/* Preferences Form content */}
        <div className="flex-grow p-4 md:p-10 overflow-y-auto max-w-2xl mx-auto w-full">
          <div className="glass-panel p-8 border-white/10 bg-white/2">
            <h2 className="font-display text-xl font-bold text-white mb-1 flex items-center gap-2 uppercase tracking-tight italic">
              <span className="material-symbols-outlined text-[#FF453A] text-xl">settings</span>
              Terminal Settings
            </h2>
            <p className="text-[10px] font-mono tracking-wider text-gray-500 uppercase mb-8">
              Configure how ZeroHour handles last-minute schedules.
            </p>

            <form onSubmit={handleSave} className="flex flex-col gap-6">
              
              {/* Google account connection details */}
              <div className="p-4 rounded-xl border border-white/5 bg-white/3 flex items-center justify-between text-xs">
                <div className="flex items-center gap-3">
                  <span className="material-symbols-outlined text-[#FF453A] text-xl">calendar_today</span>
                  <div>
                    <p className="font-bold text-white uppercase tracking-tight text-[11px]">Google Calendar Integration</p>
                    <p className="text-[10px] text-gray-500 font-mono mt-0.5">Connected as {user?.email}</p>
                  </div>
                </div>
                <span className="badge bg-[#14B8A6]/10 text-[#14B8A6] border border-[#14B8A6]/20">
                  ACTIVE
                </span>
              </div>

              {/* Email Nudges */}
              <div className="flex items-start justify-between gap-4 border-t border-white/5 pt-5">
                <div className="flex items-start gap-3">
                  <span className="material-symbols-outlined text-gray-400 text-lg mt-0.5">mail</span>
                  <div>
                    <h4 className="text-xs font-bold text-white uppercase tracking-tight">Email Nudges</h4>
                    <p className="text-[10px] text-gray-500 mt-1 uppercase tracking-wide leading-relaxed">
                      Receive reminder alerts via Google Mail client when deadlines are 24h, 6h, and 1h away.
                    </p>
                  </div>
                </div>
                <input 
                  type="checkbox" 
                  checked={preferences.emailNudges}
                  onChange={(e) => setPreferences(p => ({ ...p, emailNudges: e.target.checked }))}
                  className="w-4 h-4 text-[#FF453A] bg-transparent border-gray-600 rounded cursor-pointer accent-[#FF453A] mt-1"
                />
              </div>

              {/* In-app Nudges */}
              <div className="flex items-start justify-between gap-4 border-t border-white/5 pt-5">
                <div className="flex items-start gap-3">
                  <span className="material-symbols-outlined text-gray-400 text-lg mt-0.5">error</span>
                  <div>
                    <h4 className="text-xs font-bold text-white uppercase tracking-tight">In-App Alerts</h4>
                    <p className="text-[10px] text-gray-500 mt-1 uppercase tracking-wide leading-relaxed">
                      Show real-time badge count indicators and critical warnings inside the Operation Center.
                    </p>
                  </div>
                </div>
                <input 
                  type="checkbox" 
                  checked={preferences.inAppNudges}
                  onChange={(e) => setPreferences(p => ({ ...p, inAppNudges: e.target.checked }))}
                  className="w-4 h-4 text-[#FF453A] bg-transparent border-gray-600 rounded cursor-pointer accent-[#FF453A] mt-1"
                />
              </div>

              {/* Timezone */}
              <div className="flex flex-col gap-1.5 border-t border-white/5 pt-5">
                <label className="text-[9px] font-mono font-bold tracking-widest uppercase text-gray-400">Scheduling Timezone</label>
                <select 
                  value={preferences.timezone}
                  onChange={(e) => setPreferences(p => ({ ...p, timezone: e.target.value }))}
                  className="input-field font-mono bg-[#13151a]"
                >
                  <option value="Asia/Kolkata">Asia/Kolkata (IST - UTC+5:30)</option>
                  <option value="UTC">Coordinated Universal Time (UTC)</option>
                </select>
                <span className="text-[9px] text-gray-500 mt-1 uppercase tracking-wider leading-relaxed">
                  Events and alarms default to this zone for scheduling calculations.
                </span>
              </div>

              <button 
                type="submit" 
                disabled={saving}
                className="btn btn-primary w-full mt-4 py-4 flex items-center justify-center gap-2 text-[10px]"
              >
                {saving ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Saving Preferences...
                  </>
                ) : saved ? (
                  <>
                    <span className="material-symbols-outlined text-sm text-[#14B8A6]">check_circle</span>
                    <span className="text-[#14B8A6]">Saved ✓</span>
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-sm">save</span>
                    Save Settings
                  </>
                )}
              </button>
            </form>
          </div>
        </div>

      </main>
    </div>
  );
}
