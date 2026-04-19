import React from 'react';
import { Wifi, WifiOff } from 'lucide-react';

export function ConnectionIndicator({ connected }: { connected: boolean }) {
  return (
    <div className="flex items-center gap-1.5">
      {connected ? (
        <>
          <span className="relative flex h-2.5 w-2.5">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-green-500"></span>
          </span>
          <Wifi className="w-4 h-4 text-green-600" />
          <span className="text-xs text-green-600 font-medium">Live</span>
        </>
      ) : (
        <>
          <span className="relative flex h-2.5 w-2.5">
            <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-red-500"></span>
          </span>
          <WifiOff className="w-4 h-4 text-red-500" />
          <span className="text-xs text-red-500 font-medium">Disconnected</span>
        </>
      )}
    </div>
  );
}
