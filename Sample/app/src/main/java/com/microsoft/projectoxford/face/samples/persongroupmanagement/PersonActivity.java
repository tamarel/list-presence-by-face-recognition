//

// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license.
//
// Project Oxford: http://ProjectOxford.ai
//
// ProjectOxford SDK Github:
// https://github.com/Microsoft/ProjectOxfordSDK-Windows
//
// Copyright (c) Microsoft Corporation
// All rights reserved.
//
// MIT License:
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED ""AS IS"", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
package com.microsoft.projectoxford.face.samples.persongroupmanagement;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.camera2.params.Face;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.contract.CreatePersonResult;
import com.microsoft.projectoxford.face.contract.FaceRectangle;
import com.microsoft.projectoxford.face.samples.AboutUsActivity;
import com.microsoft.projectoxford.face.samples.CalendarActivity;
import com.microsoft.projectoxford.face.samples.MainActivity;
import com.microsoft.projectoxford.face.samples.R;
import com.microsoft.projectoxford.face.samples.helper.ImageHelper;
import com.microsoft.projectoxford.face.samples.helper.LogHelper;
import com.microsoft.projectoxford.face.samples.helper.SampleApp;
import com.microsoft.projectoxford.face.samples.helper.SelectImageActivity;
import com.microsoft.projectoxford.face.samples.helper.StorageHelper;
import com.parse.Parse;
import com.parse.ParseObject;
import com.parse.ParseUser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

//this class manage person, name ,id and faces.

public class PersonActivity extends ActionBarActivity {
    static Intent indata;
    // Background task of adding a person to person group.
    class AddPersonTask extends AsyncTask<String, String, String> {
        // Indicate the next step is to add face in this person, or finish editing this person.
        boolean mAddFace;

        //add person task
        AddPersonTask (boolean addFace) {
            mAddFace = addFace;
        }

        @Override
        protected String doInBackground(String... params) {
            // Get an instance of face service client.
            FaceServiceClient faceServiceClient = SampleApp.getFaceServiceClient();
            try{
                publishProgress("Syncing with server to add person...");
                addLog("Request: Creating Person in person group" + params[0]);

                // Start the request to creating person.
                CreatePersonResult createPersonResult = faceServiceClient.createPerson(
                        params[0],
                        getString(R.string.user_provided_person_name),
                        getString(R.string.user_provided_description_data));

                return createPersonResult.personId.toString();
            } catch (Exception e) {
                publishProgress(e.getMessage());
                addLog(e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            setUiBeforeBackgroundTask();
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            setUiDuringBackgroundTask(progress[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();

            if (result != null) {
                addLog("Response: Success. Person " + result + " created.");
                personId = result;
                setInfo("Successfully Synchronized!");

                if (mAddFace) {
                    addFace();
                } else {
                    doneAndSave();
                }
            }
        }
    }
//delete person
    class DeleteFaceTask extends AsyncTask<String, String, String> {
        String mPersonGroupId;
        UUID mPersonId;

        DeleteFaceTask(String personGroupId, String personId) {
            mPersonGroupId = personGroupId;
            mPersonId = UUID.fromString(personId);
        }

        @Override
        protected String doInBackground(String... params) {
            // Get an instance of face service client.
            FaceServiceClient faceServiceClient = SampleApp.getFaceServiceClient();
            try{
                publishProgress("Deleting selected faces...");
                addLog("Request: Deleting face " + params[0]);

                UUID faceId = UUID.fromString(params[0]);
                faceServiceClient.deletePersonFace(personGroupId, mPersonId, faceId);
                return params[0];
            } catch (Exception e) {
                publishProgress(e.getMessage());
                addLog(e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            setUiBeforeBackgroundTask();
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            setUiDuringBackgroundTask(progress[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            progressDialog.dismiss();

            if (result != null) {
                setInfo("Face " + result + " successfully deleted");
                addLog("Response: Success. Deleting face " + result + " succeed");
            }
        }
    }

    private void setUiBeforeBackgroundTask() {
        progressDialog.show();
    }

    // Show the status of background detection task on screen.
    private void setUiDuringBackgroundTask(String progress) {
        progressDialog.setMessage(progress);
        setInfo(progress);
    }
//initiliaize
    boolean addNewPerson;
    String personId;
    String personIdNumber;
    String personGroupId;
    String oldPersonName;
    String courseName,codeCourse,courseId;
    private static final int REQUEST_SELECT_IMAGE = 0;

    FaceGridViewAdapter faceGridViewAdapter;

    // Progress dialog popped up when communicating with server.
    ProgressDialog progressDialog;

    //on create
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            addNewPerson = bundle.getBoolean("AddNewPerson");
            personGroupId = bundle.getString("PersonGroupId");
            oldPersonName = bundle.getString("PersonName");
            courseName = bundle.getString("courseName");
            codeCourse = bundle.getString("codeCourse");


            if (!addNewPerson) {
                personId = bundle.getString("PersonId");
                personIdNumber = bundle.getString("PersonIdNumber");
            }
        }

        initializeGridView();

        EditText editTextPersonName = (EditText)findViewById(R.id.edit_person_name);
        editTextPersonName.setText(oldPersonName);
        EditText editTextPersonID = (EditText)findViewById(R.id.edit_person_id);
        editTextPersonID.setText(personIdNumber);

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(getString(R.string.progress_dialog_title));
    }

    private void initializeGridView() {
        GridView gridView = (GridView) findViewById(R.id.gridView_faces);

        gridView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
        gridView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            @Override
            public void onItemCheckedStateChanged(
                    ActionMode mode, int position, long id, boolean checked) {
                faceGridViewAdapter.faceChecked.set(position, checked);

                GridView gridView = (GridView) findViewById(R.id.gridView_faces);
                gridView.setAdapter(faceGridViewAdapter);
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.menu_delete_items, menu);

                faceGridViewAdapter.longPressed = true;

                GridView gridView = (GridView) findViewById(R.id.gridView_faces);
                gridView.setAdapter(faceGridViewAdapter);

                Button addNewItem = (Button) findViewById(R.id.add_face);
                addNewItem.setEnabled(false);

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_delete_items:
                        deleteSelectedItems();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                faceGridViewAdapter.longPressed = false;

                for (int i = 0; i < faceGridViewAdapter.faceChecked.size(); ++i) {
                    faceGridViewAdapter.faceChecked.set(i, false);
                }

                GridView gridView = (GridView) findViewById(R.id.gridView_faces);
                gridView.setAdapter(faceGridViewAdapter);

                Button addNewItem = (Button) findViewById(R.id.add_face);
                addNewItem.setEnabled(true);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        faceGridViewAdapter = new FaceGridViewAdapter();
        GridView gridView = (GridView) findViewById(R.id.gridView_faces);
        gridView.setAdapter(faceGridViewAdapter);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean("AddNewPerson", addNewPerson);
        outState.putString("PersonId", personId);
        outState.putString("PersonGroupId", personGroupId);
        outState.putString("OldPersonName", oldPersonName);
        outState.putString("PersonIdNumber", personIdNumber);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        addNewPerson = savedInstanceState.getBoolean("AddNewPerson");
        personId = savedInstanceState.getString("PersonId");
        personGroupId = savedInstanceState.getString("PersonGroupId");
        oldPersonName = savedInstanceState.getString("OldPersonName");
        personIdNumber = savedInstanceState.getString("PersonIdNumber");
    }

    public void doneAndSave(View view) {
        if (personId == null) {
            new AddPersonTask(false).execute(personGroupId);
        } else {
            doneAndSave();
        }
    }

    public void addFace(View view) {
        if (personId == null) {
            new AddPersonTask(true).execute(personGroupId);
        } else {
            addFace();
        }
    }

    private void doneAndSave() {
        TextView textWarning = (TextView)findViewById(R.id.info);
        EditText editTextPersonName = (EditText)findViewById(R.id.edit_person_name);
        String newPersonName = editTextPersonName.getText().toString();

        EditText editTextPersonID = (EditText)findViewById(R.id.edit_person_id);
        String newPersonID = editTextPersonID.getText().toString();
        if (newPersonName.equals("")&& newPersonID.equals("")) {
            textWarning.setText("you must fill all the fields");
            return;
        }
       if ( StorageHelper.createPerson(newPersonName, newPersonID, personId, personGroupId,
                courseName,codeCourse ,PersonActivity.this) ==false)
           Toast.makeText(PersonActivity.this,"something went wrong(maybe you try to change name or ID",Toast.LENGTH_LONG).show();

        //StorageHelper.setPersonName(personId, newPersonName, personGroupId, PersonActivity.this);

        finish();
    }

    private void addFace() {
        setInfo("");
        Intent intent = new Intent(this, SelectImageActivity.class);
        startActivityForResult(intent, REQUEST_SELECT_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode)
        {
            case REQUEST_SELECT_IMAGE:
                if (resultCode == RESULT_OK) {
                    EditText editTextPersonName = (EditText)findViewById(R.id.edit_person_name);
                    EditText editTextPersonid = (EditText)findViewById(R.id.edit_person_id);
                    indata=data;
                    Uri uriImagePicked = data.getData();
                    Intent intent = new Intent(this, AddFaceToPersonActivity.class);
                    intent.putExtra("PersonId",personId);
                    intent.putExtra("courseName",courseName);
                    intent.putExtra("code",codeCourse);
                    intent.putExtra("PersonIdNumber",editTextPersonid.getText().toString());

                    intent.putExtra("PersonName", editTextPersonName.getText().toString());
                    intent.putExtra("PersonGroupId", personGroupId);
                    intent.putExtra("ImageUriStr", uriImagePicked.toString());
                    startActivity(intent);
                }
                break;
            default:
                break;
        }
    }

//delete photos
    private void deleteSelectedItems() {
        List<String> newFaceIdList = new ArrayList<>();
        List<Boolean> newFaceChecked = new ArrayList<>();
        List<String> faceIdsToDelete = new ArrayList<>();
        for (int i = 0; i < faceGridViewAdapter.faceChecked.size(); ++i) {
            boolean checked = faceGridViewAdapter.faceChecked.get(i);
            if (checked) {
                String faceId = faceGridViewAdapter.faceIdList.get(i);
                faceIdsToDelete.add(faceId);
                new DeleteFaceTask(personGroupId, personId).execute(faceId);
            } else {
                newFaceIdList.add(faceGridViewAdapter.faceIdList.get(i));
                newFaceChecked.add(false);
            }
        }

        StorageHelper.deleteFaces(faceIdsToDelete,personId, this);

        faceGridViewAdapter.faceIdList = newFaceIdList;
        faceGridViewAdapter.faceChecked = newFaceChecked;
        faceGridViewAdapter.notifyDataSetChanged();
    }

    // Add a log item.
    private void addLog(String log) {
        LogHelper.addIdentificationLog(log);
    }

    // Set the information panel on screen.
    private void setInfo(String info) {
        TextView textView = (TextView) findViewById(R.id.info);
        textView.setText(info);
    }

    private class FaceGridViewAdapter extends BaseAdapter {
        List<String> faceIdList;
        List<Boolean> faceChecked;
        boolean longPressed;

        FaceGridViewAdapter() {
            longPressed = false;
            faceIdList = new ArrayList<>();
            faceChecked = new ArrayList<>();

            Set<String> faceIdSet = StorageHelper.getAllFaceIdsByStudent(personId, PersonActivity.this);
            for (String faceId: faceIdSet) {
                faceIdList.add(faceId);
                faceChecked.add(false);
            }
        }

        @Override
        public int getCount() {
            return faceIdList.size();
        }

        @Override
        public Object getItem(int position) {
            return faceIdList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            // set the item view
            if (convertView == null) {
                LayoutInflater layoutInflater
                        = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = layoutInflater.inflate(
                        R.layout.item_face_with_checkbox, parent, false);
            }
            convertView.setId(position);

            Uri uri = Uri.parse(StorageHelper.getFaceUriFromParse(
                    faceIdList.get(position), personId,PersonActivity.this));
            ((ImageView)convertView.findViewById(R.id.image_face)).setImageURI(uri);

            // set the checked status of the item
            CheckBox checkBox = (CheckBox) convertView.findViewById(R.id.checkbox_face);
            if (longPressed) {
                checkBox.setVisibility(View.VISIBLE);

                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        faceChecked.set(position, isChecked);
                    }
                });
                checkBox.setChecked(faceChecked.get(position));
            } else {
                checkBox.setVisibility(View.INVISIBLE);
            }

            return convertView;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_list_option, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.menu_signOut) {
            ParseUser.logOutInBackground();
            Intent intent = new Intent(PersonActivity.this,MainActivity.class);

            startActivity(intent);
            return true;
        }
        else if ( id == R.id.menu_aboutUs){
            Intent intent = new Intent(PersonActivity.this,AboutUsActivity.class);
            intent.putExtra("userName", ParseUser.getCurrentUser().getUsername());
            startActivity(intent);
            return true;
        }
        else if ( id == R.id.menu_calendar){
            Intent intent = new Intent(PersonActivity.this,CalendarActivity.class);
            intent.putExtra("userName", ParseUser.getCurrentUser().getUsername());

            startActivity(intent);
            return true;
        }
        else if ( id == R.id.menu_addCourse){
            Intent intent = new Intent(PersonActivity.this,PersonGroupActivity.class);
            intent.putExtra("userName", ParseUser.getCurrentUser().getUsername());
            intent.putExtra("AddNewPersonGroup",true);
            String personGroupId = UUID.randomUUID().toString();
            intent.putExtra("PersonGroupName", "");
            intent.putExtra("PersonGroupId", personGroupId);
            startActivity(intent);
            return true;
        }
        else if ( id == R.id.menu_goMenu){

            Intent intent = new Intent(PersonActivity.this,MenuActivity.class);
            intent.putExtra("userName", ParseUser.getCurrentUser().getUsername());

            startActivity(intent);
            return true;
        }
        else if ( id == R.id.menu_settings){
            Intent intent = new Intent(PersonActivity.this,SettingsActivity.class);
            intent.putExtra("userName", ParseUser.getCurrentUser().getUsername());

            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
