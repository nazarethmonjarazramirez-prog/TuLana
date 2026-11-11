package mx.cochinito.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Revisa en segundo plano si hay una versión nueva de TuLana y avisa con una notificación. */
public class UpdateWorker extends Worker {
    public UpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull
    @Override
    public Result doWork() {
        Reminders.checkUpdate(getApplicationContext());
        return Result.success();
    }
}
