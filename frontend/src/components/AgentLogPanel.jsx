import { Loader2, Check, AlertTriangle } from 'lucide-react';

export default function AgentLogPanel({ logs = [], isCompleted = false }) {
  if (logs.length === 0) return null;

  return (
    <div className="glass-panel p-6 mb-6 border-white/10">
      <div className="flex items-center justify-between border-b border-white/10 pb-3 mb-4">
        <h3 className="font-display text-sm font-bold uppercase tracking-wider flex items-center gap-2 text-white">
          {!isCompleted && <Loader2 className="w-4 h-4 text-[#FF453A] animate-spin" />}
          Agent Orchestrator logs
        </h3>
        <span className={`font-mono text-[9px] font-bold px-2 py-0.5 rounded-full border ${
          isCompleted ? 'bg-[#14B8A6]/10 text-[#14B8A6] border-[#14B8A6]/20' : 'bg-[#FF453A]/10 text-[#FF453A] border-[#FF453A]/20 animate-pulse'
        }`}>
          {isCompleted ? 'PIPELINE COMPLETE' : 'PROCESSING'}
        </span>
      </div>

      <div className="flex flex-col gap-3 font-mono text-xs max-h-[300px] overflow-y-auto pr-2">
        {logs.map((log, idx) => {
          const isDone = log.status === 'DONE';
          const isError = log.status === 'ERROR';

          return (
            <div key={idx} className="flex items-start gap-3 border-l border-white/5 pl-4 relative py-0.5">
              {/* Vertical line indicator icon wrapper */}
              <div className="absolute left-[-8px] top-[8px] w-4 h-4 rounded-full flex items-center justify-center bg-[#0D0D0D]">
                {isDone ? (
                  <Check className="w-3 h-3 text-[#14B8A6]" />
                ) : isError ? (
                  <AlertTriangle className="w-3 h-3 text-[#FF453A]" />
                ) : (
                  <Loader2 className="w-3 h-3 text-[#FF453A] animate-spin" />
                )}
              </div>

              <div className="flex flex-col gap-1 w-full">
                <div className="flex items-center gap-2">
                  <span className={`text-[8px] px-1.5 py-0.5 rounded font-bold border ${
                    log.agent === 'PlannerAgent' ? 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20' :
                    log.agent === 'PrioritizerAgent' ? 'bg-[#F59E0B]/10 text-[#F59E0B] border-[#F59E0B]/20' :
                    log.agent === 'SchedulerAgent' ? 'bg-blue-500/10 text-blue-400 border-blue-500/20' :
                    log.agent === 'NudgeAgent' ? 'bg-[#14B8A6]/10 text-[#14B8A6] border-[#14B8A6]/20' :
                    'bg-gray-500/10 text-gray-400 border-gray-500/20'
                  }`}>
                    {log.agent}
                  </span>

                  <span className="text-[8px] text-gray-500 font-mono">
                    {(() => {
                      if (!log.timestamp) return '';
                      const parsedInt = /^\d+$/.test(String(log.timestamp)) ? parseInt(String(log.timestamp)) : NaN;
                      const date = !isNaN(parsedInt) ? new Date(parsedInt) : new Date(log.timestamp);
                      return !isNaN(date.getTime()) ? date.toLocaleTimeString() : '';
                    })()}
                  </span>
                </div>
                
                <p className="text-gray-300 font-mono text-[11px] leading-relaxed">{log.message}</p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
