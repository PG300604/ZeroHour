import { useState, useEffect, useRef, useCallback } from 'react';
import { Check } from 'lucide-react';
import { api } from '../services/api';

export default function NotificationBell() {
  const [notifications, setNotifications] = useState([]);
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef(null);

  const fetchNotifications = useCallback(async () => {
    try {
      const list = await api.getNotifications();
      setNotifications(list || []);
    } catch (e) {
      console.error('Failed to load notifications', e);
    }
  }, []);

  useEffect(() => {
    let active = true;
    const load = async () => {
      if (active) {
        await fetchNotifications();
      }
    };
    load();

    const interval = setInterval(() => {
      if (active) {
        fetchNotifications();
      }
    }, 15000);

    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [fetchNotifications]);

  useEffect(() => {
    function handleClickOutside(event) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const unreadCount = notifications.filter(n => !n.read).length;

  const handleMarkAsRead = async (id, e) => {
    e.stopPropagation();
    try {
      await api.markNotificationRead(id);
      fetchNotifications();
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button 
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2.5 rounded-lg hover:bg-white/5 transition-all text-[#94a3b8] hover:text-[#FF453A]"
      >
        <span className="material-symbols-outlined text-2xl" style={{ fontVariationSettings: "'FILL' 0" }}>notifications</span>
        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 w-2 h-2 bg-[#FF453A] rounded-full panic-pulse" />
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-3 w-80 glass-panel p-4 z-50 animate-slide-up shadow-2xl border-white/10 rounded-xl">
          <div className="flex items-center justify-between border-b border-white/10 pb-2 mb-3">
            <h4 className="font-mono text-xs tracking-wider uppercase font-bold text-white">Alert Logs</h4>
            <span className="font-mono text-[10px] text-[#FF453A] font-bold bg-[#FF453A]/10 border border-[#FF453A]/20 px-2 py-0.5 rounded">
              {unreadCount} PENDING
            </span>
          </div>

          <div className="flex flex-col gap-2 max-h-[250px] overflow-y-auto pr-1">
            {notifications.length === 0 ? (
              <p className="text-[10px] font-mono tracking-widest text-[#94a3b8]/50 text-center py-6 uppercase">
                No active logs
              </p>
            ) : (
              notifications.map((n) => {
                const typeLabel = n.nudgeType || n.type || 'ALERT';
                
                return (
                  <div 
                    key={n.id} 
                    className={`p-3 rounded-lg border text-xs flex flex-col gap-1 transition-all ${
                      n.read 
                        ? 'bg-transparent border-white/5 opacity-50' 
                        : 'bg-white/3 border-[#FF453A]/20 shadow-sm'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className={`font-mono text-[9px] font-bold px-1.5 py-0.5 rounded uppercase tracking-wider ${
                        typeLabel.includes('1H') ? 'bg-[#FF453A]/20 text-[#FF453A]' :
                        typeLabel.includes('6H') ? 'bg-[#F59E0B]/20 text-[#F59E0B]' :
                        'bg-blue-500/20 text-blue-400'
                      }`}>
                        {typeLabel.replace('REMINDER_', '')}
                      </span>
                      {!n.read && (
                        <button 
                          onClick={(e) => handleMarkAsRead(n.id, e)}
                          className="text-[#94a3b8] hover:text-white p-0.5 rounded hover:bg-white/5 transition-colors"
                          title="Acknowledge"
                        >
                          <Check className="w-3.5 h-3.5 text-[#14B8A6]" />
                        </button>
                      )}
                    </div>
                    <p className="text-gray-300 text-[11px] leading-relaxed mt-1">
                      {n.body || 'System alert triggered. Action required.'}
                    </p>
                    <div className="text-[9px] font-mono text-[#94a3b8]/60 text-right mt-1">
                      {n.scheduledAt ? new Date(n.scheduledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}
