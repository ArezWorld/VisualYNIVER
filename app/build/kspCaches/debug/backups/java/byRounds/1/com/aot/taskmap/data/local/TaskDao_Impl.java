package com.aot.taskmap.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.aot.taskmap.domain.model.Task;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TaskDao_Impl implements TaskDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Task> __insertionAdapterOfTask;

  private final EntityDeletionOrUpdateAdapter<Task> __deletionAdapterOfTask;

  private final EntityDeletionOrUpdateAdapter<Task> __updateAdapterOfTask;

  private final SharedSQLiteStatement __preparedStmtOfDeleteTaskById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateTaskCompletion;

  public TaskDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTask = new EntityInsertionAdapter<Task>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `tasks` (`id`,`title`,`description`,`latitude`,`longitude`,`address`,`radius`,`markerColor`,`markerIcon`,`autoRemoveAfterTrigger`,`isCompleted`,`isNotificationEnabled`,`createdAt`,`completedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Task entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getDescription());
        statement.bindDouble(4, entity.getLatitude());
        statement.bindDouble(5, entity.getLongitude());
        statement.bindString(6, entity.getAddress());
        statement.bindLong(7, entity.getRadius());
        statement.bindLong(8, entity.getMarkerColor());
        statement.bindString(9, entity.getMarkerIcon());
        final int _tmp = entity.getAutoRemoveAfterTrigger() ? 1 : 0;
        statement.bindLong(10, _tmp);
        final int _tmp_1 = entity.isCompleted() ? 1 : 0;
        statement.bindLong(11, _tmp_1);
        final int _tmp_2 = entity.isNotificationEnabled() ? 1 : 0;
        statement.bindLong(12, _tmp_2);
        statement.bindLong(13, entity.getCreatedAt());
        if (entity.getCompletedAt() == null) {
          statement.bindNull(14);
        } else {
          statement.bindLong(14, entity.getCompletedAt());
        }
      }
    };
    this.__deletionAdapterOfTask = new EntityDeletionOrUpdateAdapter<Task>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `tasks` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Task entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfTask = new EntityDeletionOrUpdateAdapter<Task>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `tasks` SET `id` = ?,`title` = ?,`description` = ?,`latitude` = ?,`longitude` = ?,`address` = ?,`radius` = ?,`markerColor` = ?,`markerIcon` = ?,`autoRemoveAfterTrigger` = ?,`isCompleted` = ?,`isNotificationEnabled` = ?,`createdAt` = ?,`completedAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Task entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getDescription());
        statement.bindDouble(4, entity.getLatitude());
        statement.bindDouble(5, entity.getLongitude());
        statement.bindString(6, entity.getAddress());
        statement.bindLong(7, entity.getRadius());
        statement.bindLong(8, entity.getMarkerColor());
        statement.bindString(9, entity.getMarkerIcon());
        final int _tmp = entity.getAutoRemoveAfterTrigger() ? 1 : 0;
        statement.bindLong(10, _tmp);
        final int _tmp_1 = entity.isCompleted() ? 1 : 0;
        statement.bindLong(11, _tmp_1);
        final int _tmp_2 = entity.isNotificationEnabled() ? 1 : 0;
        statement.bindLong(12, _tmp_2);
        statement.bindLong(13, entity.getCreatedAt());
        if (entity.getCompletedAt() == null) {
          statement.bindNull(14);
        } else {
          statement.bindLong(14, entity.getCompletedAt());
        }
        statement.bindLong(15, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteTaskById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM tasks WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateTaskCompletion = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE tasks SET isCompleted = ?, completedAt = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertTask(final Task task, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTask.insertAndReturnId(task);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertTasks(final List<Task> tasks, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTask.insert(tasks);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteTask(final Task task, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfTask.handle(task);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateTask(final Task task, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfTask.handle(task);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteTaskById(final long taskId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteTaskById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, taskId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteTaskById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateTaskCompletion(final long taskId, final boolean isCompleted,
      final Long completedAt, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateTaskCompletion.acquire();
        int _argIndex = 1;
        final int _tmp = isCompleted ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        if (completedAt == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, completedAt);
        }
        _argIndex = 3;
        _stmt.bindLong(_argIndex, taskId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateTaskCompletion.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Task>> getAllTasks() {
    final String _sql = "SELECT * FROM tasks ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tasks"}, new Callable<List<Task>>() {
      @Override
      @NonNull
      public List<Task> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "address");
          final int _cursorIndexOfRadius = CursorUtil.getColumnIndexOrThrow(_cursor, "radius");
          final int _cursorIndexOfMarkerColor = CursorUtil.getColumnIndexOrThrow(_cursor, "markerColor");
          final int _cursorIndexOfMarkerIcon = CursorUtil.getColumnIndexOrThrow(_cursor, "markerIcon");
          final int _cursorIndexOfAutoRemoveAfterTrigger = CursorUtil.getColumnIndexOrThrow(_cursor, "autoRemoveAfterTrigger");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfIsNotificationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isNotificationEnabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<Task> _result = new ArrayList<Task>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Task _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final double _tmpLatitude;
            _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            final double _tmpLongitude;
            _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            final String _tmpAddress;
            _tmpAddress = _cursor.getString(_cursorIndexOfAddress);
            final int _tmpRadius;
            _tmpRadius = _cursor.getInt(_cursorIndexOfRadius);
            final int _tmpMarkerColor;
            _tmpMarkerColor = _cursor.getInt(_cursorIndexOfMarkerColor);
            final String _tmpMarkerIcon;
            _tmpMarkerIcon = _cursor.getString(_cursorIndexOfMarkerIcon);
            final boolean _tmpAutoRemoveAfterTrigger;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAutoRemoveAfterTrigger);
            _tmpAutoRemoveAfterTrigger = _tmp != 0;
            final boolean _tmpIsCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp_1 != 0;
            final boolean _tmpIsNotificationEnabled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsNotificationEnabled);
            _tmpIsNotificationEnabled = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new Task(_tmpId,_tmpTitle,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpAddress,_tmpRadius,_tmpMarkerColor,_tmpMarkerIcon,_tmpAutoRemoveAfterTrigger,_tmpIsCompleted,_tmpIsNotificationEnabled,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<Task>> getActiveTasks() {
    final String _sql = "SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tasks"}, new Callable<List<Task>>() {
      @Override
      @NonNull
      public List<Task> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "address");
          final int _cursorIndexOfRadius = CursorUtil.getColumnIndexOrThrow(_cursor, "radius");
          final int _cursorIndexOfMarkerColor = CursorUtil.getColumnIndexOrThrow(_cursor, "markerColor");
          final int _cursorIndexOfMarkerIcon = CursorUtil.getColumnIndexOrThrow(_cursor, "markerIcon");
          final int _cursorIndexOfAutoRemoveAfterTrigger = CursorUtil.getColumnIndexOrThrow(_cursor, "autoRemoveAfterTrigger");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfIsNotificationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isNotificationEnabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<Task> _result = new ArrayList<Task>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Task _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final double _tmpLatitude;
            _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            final double _tmpLongitude;
            _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            final String _tmpAddress;
            _tmpAddress = _cursor.getString(_cursorIndexOfAddress);
            final int _tmpRadius;
            _tmpRadius = _cursor.getInt(_cursorIndexOfRadius);
            final int _tmpMarkerColor;
            _tmpMarkerColor = _cursor.getInt(_cursorIndexOfMarkerColor);
            final String _tmpMarkerIcon;
            _tmpMarkerIcon = _cursor.getString(_cursorIndexOfMarkerIcon);
            final boolean _tmpAutoRemoveAfterTrigger;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAutoRemoveAfterTrigger);
            _tmpAutoRemoveAfterTrigger = _tmp != 0;
            final boolean _tmpIsCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp_1 != 0;
            final boolean _tmpIsNotificationEnabled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsNotificationEnabled);
            _tmpIsNotificationEnabled = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new Task(_tmpId,_tmpTitle,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpAddress,_tmpRadius,_tmpMarkerColor,_tmpMarkerIcon,_tmpAutoRemoveAfterTrigger,_tmpIsCompleted,_tmpIsNotificationEnabled,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<Task>> getCompletedTasks() {
    final String _sql = "SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tasks"}, new Callable<List<Task>>() {
      @Override
      @NonNull
      public List<Task> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "address");
          final int _cursorIndexOfRadius = CursorUtil.getColumnIndexOrThrow(_cursor, "radius");
          final int _cursorIndexOfMarkerColor = CursorUtil.getColumnIndexOrThrow(_cursor, "markerColor");
          final int _cursorIndexOfMarkerIcon = CursorUtil.getColumnIndexOrThrow(_cursor, "markerIcon");
          final int _cursorIndexOfAutoRemoveAfterTrigger = CursorUtil.getColumnIndexOrThrow(_cursor, "autoRemoveAfterTrigger");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfIsNotificationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isNotificationEnabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<Task> _result = new ArrayList<Task>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Task _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final double _tmpLatitude;
            _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            final double _tmpLongitude;
            _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            final String _tmpAddress;
            _tmpAddress = _cursor.getString(_cursorIndexOfAddress);
            final int _tmpRadius;
            _tmpRadius = _cursor.getInt(_cursorIndexOfRadius);
            final int _tmpMarkerColor;
            _tmpMarkerColor = _cursor.getInt(_cursorIndexOfMarkerColor);
            final String _tmpMarkerIcon;
            _tmpMarkerIcon = _cursor.getString(_cursorIndexOfMarkerIcon);
            final boolean _tmpAutoRemoveAfterTrigger;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAutoRemoveAfterTrigger);
            _tmpAutoRemoveAfterTrigger = _tmp != 0;
            final boolean _tmpIsCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp_1 != 0;
            final boolean _tmpIsNotificationEnabled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsNotificationEnabled);
            _tmpIsNotificationEnabled = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new Task(_tmpId,_tmpTitle,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpAddress,_tmpRadius,_tmpMarkerColor,_tmpMarkerIcon,_tmpAutoRemoveAfterTrigger,_tmpIsCompleted,_tmpIsNotificationEnabled,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getTaskById(final long taskId, final Continuation<? super Task> $completion) {
    final String _sql = "SELECT * FROM tasks WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, taskId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Task>() {
      @Override
      @Nullable
      public Task call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "address");
          final int _cursorIndexOfRadius = CursorUtil.getColumnIndexOrThrow(_cursor, "radius");
          final int _cursorIndexOfMarkerColor = CursorUtil.getColumnIndexOrThrow(_cursor, "markerColor");
          final int _cursorIndexOfMarkerIcon = CursorUtil.getColumnIndexOrThrow(_cursor, "markerIcon");
          final int _cursorIndexOfAutoRemoveAfterTrigger = CursorUtil.getColumnIndexOrThrow(_cursor, "autoRemoveAfterTrigger");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfIsNotificationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isNotificationEnabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final Task _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final double _tmpLatitude;
            _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            final double _tmpLongitude;
            _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            final String _tmpAddress;
            _tmpAddress = _cursor.getString(_cursorIndexOfAddress);
            final int _tmpRadius;
            _tmpRadius = _cursor.getInt(_cursorIndexOfRadius);
            final int _tmpMarkerColor;
            _tmpMarkerColor = _cursor.getInt(_cursorIndexOfMarkerColor);
            final String _tmpMarkerIcon;
            _tmpMarkerIcon = _cursor.getString(_cursorIndexOfMarkerIcon);
            final boolean _tmpAutoRemoveAfterTrigger;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAutoRemoveAfterTrigger);
            _tmpAutoRemoveAfterTrigger = _tmp != 0;
            final boolean _tmpIsCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp_1 != 0;
            final boolean _tmpIsNotificationEnabled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsNotificationEnabled);
            _tmpIsNotificationEnabled = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _result = new Task(_tmpId,_tmpTitle,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpAddress,_tmpRadius,_tmpMarkerColor,_tmpMarkerIcon,_tmpAutoRemoveAfterTrigger,_tmpIsCompleted,_tmpIsNotificationEnabled,_tmpCreatedAt,_tmpCompletedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getTasksWithNotifications(final Continuation<? super List<Task>> $completion) {
    final String _sql = "SELECT * FROM tasks WHERE isNotificationEnabled = 1 AND isCompleted = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<Task>>() {
      @Override
      @NonNull
      public List<Task> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "address");
          final int _cursorIndexOfRadius = CursorUtil.getColumnIndexOrThrow(_cursor, "radius");
          final int _cursorIndexOfMarkerColor = CursorUtil.getColumnIndexOrThrow(_cursor, "markerColor");
          final int _cursorIndexOfMarkerIcon = CursorUtil.getColumnIndexOrThrow(_cursor, "markerIcon");
          final int _cursorIndexOfAutoRemoveAfterTrigger = CursorUtil.getColumnIndexOrThrow(_cursor, "autoRemoveAfterTrigger");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfIsNotificationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isNotificationEnabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<Task> _result = new ArrayList<Task>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Task _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final double _tmpLatitude;
            _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            final double _tmpLongitude;
            _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            final String _tmpAddress;
            _tmpAddress = _cursor.getString(_cursorIndexOfAddress);
            final int _tmpRadius;
            _tmpRadius = _cursor.getInt(_cursorIndexOfRadius);
            final int _tmpMarkerColor;
            _tmpMarkerColor = _cursor.getInt(_cursorIndexOfMarkerColor);
            final String _tmpMarkerIcon;
            _tmpMarkerIcon = _cursor.getString(_cursorIndexOfMarkerIcon);
            final boolean _tmpAutoRemoveAfterTrigger;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAutoRemoveAfterTrigger);
            _tmpAutoRemoveAfterTrigger = _tmp != 0;
            final boolean _tmpIsCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp_1 != 0;
            final boolean _tmpIsNotificationEnabled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsNotificationEnabled);
            _tmpIsNotificationEnabled = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new Task(_tmpId,_tmpTitle,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpAddress,_tmpRadius,_tmpMarkerColor,_tmpMarkerIcon,_tmpAutoRemoveAfterTrigger,_tmpIsCompleted,_tmpIsNotificationEnabled,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Task>> getTasksWithNotificationsFlow() {
    final String _sql = "SELECT * FROM tasks WHERE isNotificationEnabled = 1 AND isCompleted = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tasks"}, new Callable<List<Task>>() {
      @Override
      @NonNull
      public List<Task> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "address");
          final int _cursorIndexOfRadius = CursorUtil.getColumnIndexOrThrow(_cursor, "radius");
          final int _cursorIndexOfMarkerColor = CursorUtil.getColumnIndexOrThrow(_cursor, "markerColor");
          final int _cursorIndexOfMarkerIcon = CursorUtil.getColumnIndexOrThrow(_cursor, "markerIcon");
          final int _cursorIndexOfAutoRemoveAfterTrigger = CursorUtil.getColumnIndexOrThrow(_cursor, "autoRemoveAfterTrigger");
          final int _cursorIndexOfIsCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "isCompleted");
          final int _cursorIndexOfIsNotificationEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "isNotificationEnabled");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<Task> _result = new ArrayList<Task>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Task _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final double _tmpLatitude;
            _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            final double _tmpLongitude;
            _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            final String _tmpAddress;
            _tmpAddress = _cursor.getString(_cursorIndexOfAddress);
            final int _tmpRadius;
            _tmpRadius = _cursor.getInt(_cursorIndexOfRadius);
            final int _tmpMarkerColor;
            _tmpMarkerColor = _cursor.getInt(_cursorIndexOfMarkerColor);
            final String _tmpMarkerIcon;
            _tmpMarkerIcon = _cursor.getString(_cursorIndexOfMarkerIcon);
            final boolean _tmpAutoRemoveAfterTrigger;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfAutoRemoveAfterTrigger);
            _tmpAutoRemoveAfterTrigger = _tmp != 0;
            final boolean _tmpIsCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsCompleted);
            _tmpIsCompleted = _tmp_1 != 0;
            final boolean _tmpIsNotificationEnabled;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsNotificationEnabled);
            _tmpIsNotificationEnabled = _tmp_2 != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new Task(_tmpId,_tmpTitle,_tmpDescription,_tmpLatitude,_tmpLongitude,_tmpAddress,_tmpRadius,_tmpMarkerColor,_tmpMarkerIcon,_tmpAutoRemoveAfterTrigger,_tmpIsCompleted,_tmpIsNotificationEnabled,_tmpCreatedAt,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
