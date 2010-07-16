package com.imaginea.android.sugarcrm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TextView;

import com.imaginea.android.sugarcrm.provider.DatabaseHelper;
import com.imaginea.android.sugarcrm.util.ModuleField;
import com.imaginea.android.sugarcrm.util.Util;

import java.util.LinkedHashMap;
import java.util.Map;

public class EditDetailsActivity extends Activity {

    private final String TAG = "EditDetailsActivity";

    private int MODE = -1;

    private TableLayout mDetailsTable;

    private Cursor mCursor;

    private String mSugarBeanId;

    private String mModuleName;

    private String mRowId;

    private String[] mSelectFields;

    private Uri mIntentUri;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.account_details);
        findViewById(R.id.commonList).setVisibility(View.GONE);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        mModuleName = "Contacts";
        if (extras != null) {
            // i always get the module name
            mModuleName = extras.getString(RestUtilConstants.MODULE_NAME);

            mRowId = intent.getStringExtra(Util.ROW_ID);
            mSugarBeanId = intent.getStringExtra(RestUtilConstants.BEAN_ID);
        }
        // when the user comes from the relationships, intent.getData() won't be null
        mIntentUri = intent.getData();

        if (intent.getData() != null && mRowId != null) {
            // TODO: this case is intentionally left as of now
            // this case comes into picture only if the user can change the accountName to which it
            // is associated
            MODE = Util.EDIT_RELATIONSHIP_MODE;
        } else if (mRowId != null) {
            MODE = Util.EDIT_ORPHAN_MODE;
        } else if (intent.getData() != null) {
            MODE = Util.NEW_RELATIONSHIP_MODE;
        } else {
            MODE = Util.NEW_ORPHAN_MODE;
        }

        if (intent.getData() == null && MODE == Util.EDIT_ORPHAN_MODE) {
            intent.setData(Uri.withAppendedPath(DatabaseHelper.getModuleUri(mModuleName), mRowId));
        } else if (intent.getData() == null && MODE == Util.NEW_ORPHAN_MODE) {
            intent.setData(DatabaseHelper.getModuleUri(mModuleName));
        }

        mSelectFields = DatabaseHelper.getModuleProjections(mModuleName);

        if (MODE == Util.EDIT_ORPHAN_MODE || MODE == Util.EDIT_RELATIONSHIP_MODE) {
            mCursor = getContentResolver().query(getIntent().getData(), mSelectFields, null, null, DatabaseHelper.getModuleSortOrder(mModuleName));
        }
        // startManagingCursor(mCursor);
        setContents(mModuleName);
    }

    private void setContents(final String moduleName) {

        String[] detailsProjection = mSelectFields;

        TextView tv = (TextView) findViewById(R.id.headerText);
        if(MODE == Util.EDIT_ORPHAN_MODE || MODE == Util.EDIT_RELATIONSHIP_MODE){
            tv.setText("Edit " + mModuleName + " details");
        } else if(MODE == Util.NEW_ORPHAN_MODE || MODE == Util.NEW_RELATIONSHIP_MODE){
            tv.setText("New " + mModuleName + " details");
        }
        
        mDetailsTable = (TableLayout) findViewById(R.id.accountDetalsTable);

        if (MODE == Util.EDIT_ORPHAN_MODE || MODE == Util.EDIT_RELATIONSHIP_MODE) {
            mCursor.moveToFirst();
            mSugarBeanId = mCursor.getString(1); // beanId has columnIndex 1
        }

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        for (int i = 2; i < detailsProjection.length - 2; i++) {
            String fieldName = detailsProjection[i];

            // get the attributes of the moduleField
            ModuleField moduleField = DatabaseHelper.getModuleField(moduleName, fieldName);

            // do not display account_name field, i.e. user cannot modify the account name
            if (!mModuleName.equals(getString(R.string.accounts))
                                            && ModuleFields.ACCOUNT_NAME.equals(fieldName))
                continue;

            View tableRow = inflater.inflate(R.layout.edit_table_row, null);
            TextView textViewForLabel = (TextView) tableRow.findViewById(R.id.editRowLabel);
            EditText editTextForValue = (EditText) tableRow.findViewById(R.id.editRowValue);

            if (moduleField.isRequired()) {
                textViewForLabel.setText(moduleField.getLabel() + " *");
            } else {
                textViewForLabel.setText(moduleField.getLabel());
            }

            editTextForValue.setId(i);
            if (MODE == Util.EDIT_ORPHAN_MODE || MODE == Util.EDIT_RELATIONSHIP_MODE) {
                String value = mCursor.getString(mCursor.getColumnIndex(fieldName));
                if (value != null && !value.equals("")) {
                    editTextForValue.setText(value);
                }
            }
            mDetailsTable.addView(tableRow);
        }

        Button submit = new Button(EditDetailsActivity.this);
        submit.setText("Save");
        submit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] detailsProjection = mSelectFields;

                Map<String, String> modifiedValues = new LinkedHashMap<String, String>();
                if (MODE == Util.EDIT_ORPHAN_MODE || MODE == Util.EDIT_RELATIONSHIP_MODE)
                    modifiedValues.put(RestUtilConstants.ID, mSugarBeanId);

                for (int i = 2; i < detailsProjection.length - 2; i++) {

                    // do not display account_name field, i.e. user cannot modify the account name
                    if (!mModuleName.equals(getString(R.string.accounts))
                                                    && ModuleFields.ACCOUNT_NAME.equals(detailsProjection[i]))
                        continue;

                    EditText editText = (EditText) mDetailsTable.findViewById(i);
                    Log.i(TAG, detailsProjection[i] + " : " + editText.getText().toString());

                    // TODO: validation

                    // add the fieldName : fieldValue in the ContentValues
                    modifiedValues.put(detailsProjection[i], editText.getText().toString());
                }

                if (MODE == Util.EDIT_ORPHAN_MODE || MODE == Util.EDIT_RELATIONSHIP_MODE) {
                    ServiceHelper.startServiceForUpdate(getBaseContext(), Uri.withAppendedPath(DatabaseHelper.getModuleUri(moduleName), mRowId), moduleName, mSugarBeanId, modifiedValues);
                } else if (MODE == Util.NEW_RELATIONSHIP_MODE) {
                    ServiceHelper.startServiceForInsert(getBaseContext(), mIntentUri, mModuleName, modifiedValues);
                } else if (MODE == Util.NEW_ORPHAN_MODE) {
                    ServiceHelper.startServiceForInsert(getBaseContext(), DatabaseHelper.getModuleUri(mModuleName), moduleName, modifiedValues);
                }

                finish();
            }
        });
        mDetailsTable.addView(submit);

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor != null && !mCursor.isClosed())
            mCursor.close();

    }
}
