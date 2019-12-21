package in.co.unifytech.socket.utils;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import in.co.unifytech.R;

public class AsyncTaskCustomProgressDialog
{
    private final AlertDialog progressDialog;

    public AsyncTaskCustomProgressDialog(Context hostActivityContext, String title, String message)
    {
        View progressDialogLayout = View.inflate(hostActivityContext, R.layout.dialog_custom_progress_dialog, null);
        TextView txtViewProgressBar = progressDialogLayout.findViewById(R.id.txtViewProgressBar);
        txtViewProgressBar.setText(message);

        AlertDialog.Builder progressDialogBuilder = new AlertDialog.Builder(hostActivityContext)
                .setTitle(title)
                .setCancelable(false)
                .setView(progressDialogLayout);

        progressDialog = progressDialogBuilder.create();
    }

    /*public void setOnCancelListener(DialogInterface.OnCancelListener onCancelListener)
    {
        progressDialog.setOnCancelListener(onCancelListener);
    }*/

    public void show()
    {
        progressDialog.show();
    }

    public void dismiss()
    {
        progressDialog.dismiss();
    }
}
