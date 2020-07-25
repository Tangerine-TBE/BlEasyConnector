package com.tangerine.connector;

import java.util.TimerTask;

public final class BaseTimerTask extends TimerTask {
    private final ITimerListener iTimerListener;
    public BaseTimerTask(ITimerListener iTimerListener){
        this.iTimerListener = iTimerListener;
    }
    @Override
    public void run() {
        if (iTimerListener != null){
            iTimerListener.onTime();
        }
    }

    interface ITimerListener{
        void onTime();
    }

}
