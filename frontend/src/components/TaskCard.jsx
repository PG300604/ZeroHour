import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../services/api';

export default function TaskCard({ task, subtasks = [], subtaskCount, completedSubtasks }) {
  const [timeLeft, setTimeLeft] = useState('');
  const [isCriticalAlert, setIsCriticalAlert] = useState(false);

  // Lazy loading states
  const [details, setDetails] = useState(null);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [isVisible, setIsVisible] = useState(false);
  const [elementRef, setElementRef] = useState(null);

  // Intersection Observer for scroll-based loading
  useEffect(() => {
    if (!elementRef) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.disconnect(); // Stop observing once it's visible
        }
      },
      { rootMargin: '100px' } // Pre-load slightly before scrolling into view
    );
    observer.observe(elementRef);
    return () => observer.disconnect();
  }, [elementRef]);

  // Fetch details when card becomes visible
  useEffect(() => {
    if (!isVisible || subtaskCount !== undefined || details || loadingDetails) return;

    const fetchDetails = async () => {
      setLoadingDetails(true);
      try {
        const data = await api.getTask(task.id);
        setDetails({
          subtaskCount: data.subtaskCount,
          completedSubtasks: data.completedSubtasks
        });
      } catch (err) {
        console.error("Failed to lazy load task details for card", task.id, err);
      } finally {
        setLoadingDetails(false);
      }
    };

    fetchDetails();
  }, [isVisible, task.id, subtaskCount, details, loadingDetails]);

  useEffect(() => {
    if (!task.deadline) return;

    const updateTimer = () => {
      const deadlineTime = new Date(task.deadline).getTime();
      const now = new Date().getTime();
      const diff = deadlineTime - now;

      if (diff <= 0) {
        setTimeLeft('00:00:00');
        setIsCriticalAlert(true);
        return;
      }

      // Calculations for precise hh:mm:ss format
      const hours = Math.floor(diff / (1000 * 60 * 60));
      const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
      const seconds = Math.floor((diff % (1000 * 60)) / 1000);

      const timeStr = `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
      setTimeLeft(timeStr);

      // Trigger critical glow alert under 1 hour
      setIsCriticalAlert(diff < 60 * 60 * 1000);
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);
    return () => clearInterval(interval);
  }, [task.deadline]);

  // Subtask statistics (check if props exist or if we lazy-loaded them)
  const totalCount = subtaskCount !== undefined 
    ? subtaskCount 
    : (details ? details.subtaskCount : (subtasks.length > 0 ? subtasks.length : null));
    
  const completedCount = completedSubtasks !== undefined 
    ? completedSubtasks 
    : (details ? details.completedSubtasks : (subtasks.length > 0 ? subtasks.filter(s => s.status === 'DONE').length : null));

  const hasDetails = totalCount !== null && completedCount !== null;
  const percentComplete = hasDetails && totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

  // Determine priority color themes
  const isCritical = task.priority === 'CRITICAL';
  const isHigh = task.priority === 'HIGH';
  const isMedium = task.priority === 'MEDIUM';

  const themeColorClass = 
    isCritical ? 'text-[#FF453A] border-[#FF453A]/40 bg-[#FF453A]/10' :
    isHigh ? 'text-[#F59E0B] border-[#F59E0B]/30 bg-[#F59E0B]/10' :
    isMedium ? 'text-blue-400 border-blue-500/20 bg-blue-500/10' :
    'text-[#94a3b8] border-white/10 bg-white/5';

  const leftBorderColor = 
    isCritical ? 'bg-[#FF453A]' :
    isHigh ? 'bg-[#F59E0B]' :
    isMedium ? 'bg-blue-500' : 'bg-gray-600';

  const labelText = 
    isCritical ? 'MISSION CRITICAL' :
    isHigh ? 'HIGH PRIORITY' :
    isMedium ? 'NOMINAL' : 'LOW PRIORITY';

  const progressFillColor = 
    isCritical ? 'bg-[#FF453A]' :
    isHigh ? 'bg-[#F59E0B]' :
    isMedium ? 'bg-blue-500' : 'bg-gray-500';

  return (
    <Link 
      ref={setElementRef}
      to={`/task/${task.id}`}
      className={`glass-panel p-8 rounded-xl flex flex-col justify-between h-[250px] transition-all cursor-pointer relative overflow-hidden group ring-1 ring-white/10 ${
        isCriticalAlert ? 'glass-panel-glow border-[#FF453A]/30' : 'hover:border-[#FF453A]/30'
      }`}
    >
      {/* Absolute left accent bar */}
      <div className={`absolute top-0 left-0 w-[4px] h-full ${leftBorderColor} opacity-50 group-hover:opacity-100 transition-opacity`} />

      <div>
        <div className="flex justify-between items-start gap-2 mb-3">
          <div className="flex items-center gap-1.5">
            {isCritical && <span className="w-1.5 h-1.5 bg-[#FF453A] rounded-full animate-pulse"></span>}
            <span className={`px-2 py-0.5 border font-mono text-[9px] uppercase tracking-wider rounded font-bold ${themeColorClass}`}>
              {labelText}
            </span>
          </div>
          <div className="flex flex-col items-end">
            <span className={`font-mono text-xl font-bold tracking-tight ${
              isCriticalAlert ? 'text-[#FF453A] countdown-pulse' : 'text-[#94a3b8]'
            }`}>
              {timeLeft}
            </span>
            <span className="text-[8px] font-mono text-gray-500 uppercase tracking-widest mt-0.5">Est. Zero Hour</span>
          </div>
        </div>

        <h3 className="font-display text-lg font-bold text-white mb-2 uppercase tracking-tight line-clamp-1 group-hover:text-[#FF453A] transition-colors leading-tight">
          {task.title}
        </h3>
        
        <p className="text-xs text-gray-400 line-clamp-2 leading-relaxed opacity-85">
          {task.description || 'Command operations plan: Initializing task breakdown protocols.'}
        </p>
      </div>

      {/* Progress track */}
      <div className="mt-4">
        <div className="flex justify-between text-[9px] font-mono text-gray-500 mb-1 tracking-widest font-bold">
          <span className="uppercase tracking-widest">SYSTEM PROGRESS</span>
          <span>{hasDetails ? `${percentComplete}%` : 'SCANNING...'}</span>
        </div>
        <div className="h-1 bg-white/5 rounded-full overflow-hidden border border-white/5 relative">
          {!hasDetails && (
            <div className="absolute inset-0 bg-[#FF453A]/10 animate-pulse" />
          )}
          <div 
            className={`h-full ${progressFillColor} transition-all duration-500`}
            style={{ width: `${hasDetails ? percentComplete : 0}%` }}
          />
        </div>
      </div>

      {/* Footer info */}
      <div className="flex items-center gap-6 pt-4 mt-auto border-t border-white/5 text-[9px] font-mono text-gray-500 tracking-widest font-bold">
        <span className="flex items-center gap-1.5">
          <span className="material-symbols-outlined text-sm">account_tree</span>
          {hasDetails ? `${totalCount} UNITS` : 'LOADING...'}
        </span>
        
        <span className={`flex items-center gap-1.5 ${isCriticalAlert ? 'text-[#FF453A]' : ''}`}>
          <span className="material-symbols-outlined text-sm">calendar_today</span>
          {task.deadline ? new Date(task.deadline).toLocaleDateString(undefined, {month: 'short', day: 'numeric'}).toUpperCase() : 'NO DEADLINE'}
        </span>
      </div>
    </Link>
  );
}
