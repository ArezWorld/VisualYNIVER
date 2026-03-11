from datetime import datetime, timedelta
import os
import sqlite3

from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_jwt_extended import JWTManager, create_access_token, get_jwt_identity, jwt_required
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import check_password_hash, generate_password_hash

app = Flask(__name__)
app.config["SECRET_KEY"] = "aot_secret_key_change_in_production"
app.config["JWT_SECRET_KEY"] = "aot_jwt_secret_key_change_in_production"
app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///aot.db"
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = timedelta(days=7)

db = SQLAlchemy(app)
CORS(app)
jwt = JWTManager(app)


class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    email = db.Column(db.String(120), unique=True, nullable=False)
    hashed_password = db.Column(db.String(256), nullable=False)
    is_active = db.Column(db.Boolean, default=True)
    tasks = db.relationship("Task", backref="owner", lazy=True)


class Task(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(200), nullable=False)
    description = db.Column(db.String(500), default="")
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    address = db.Column(db.String(256), default="")
    radius = db.Column(db.Integer, default=100)
    marker_color = db.Column(db.Integer, default=0xFF2196F3)
    marker_icon = db.Column(db.String(64), default="pin")
    category = db.Column(db.String(64), default="general")
    auto_remove_after_trigger = db.Column(db.Boolean, default=False)
    is_completed = db.Column(db.Boolean, default=False)
    is_notification_enabled = db.Column(db.Boolean, default=True)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    completed_at = db.Column(db.DateTime, nullable=True)
    user_id = db.Column(db.Integer, db.ForeignKey("user.id"), nullable=False)


with app.app_context():
    db.create_all()


def ensure_task_columns():
    db_path = os.path.join(app.instance_path, "aot.db")
    with sqlite3.connect(db_path) as connection:
        cursor = connection.cursor()
        cursor.execute("PRAGMA table_info(task)")
        existing_columns = {row[1] for row in cursor.fetchall()}
        if "marker_color" not in existing_columns:
            cursor.execute("ALTER TABLE task ADD COLUMN marker_color INTEGER DEFAULT 4280391411")
        if "marker_icon" not in existing_columns:
            cursor.execute("ALTER TABLE task ADD COLUMN marker_icon TEXT DEFAULT 'pin'")
        if "category" not in existing_columns:
            cursor.execute("ALTER TABLE task ADD COLUMN category TEXT DEFAULT 'general'")
        if "auto_remove_after_trigger" not in existing_columns:
            cursor.execute(
                "ALTER TABLE task ADD COLUMN auto_remove_after_trigger BOOLEAN DEFAULT 0"
            )
        connection.commit()


ensure_task_columns()


def serialize_task(task):
    return {
        "id": task.id,
        "title": task.title,
        "description": task.description,
        "latitude": task.latitude,
        "longitude": task.longitude,
        "address": task.address,
        "radius": task.radius,
        "marker_color": task.marker_color,
        "marker_icon": task.marker_icon,
        "category": task.category,
        "auto_remove_after_trigger": task.auto_remove_after_trigger,
        "is_completed": task.is_completed,
        "is_notification_enabled": task.is_notification_enabled,
        "created_at": task.created_at.isoformat(),
        "completed_at": task.completed_at.isoformat() if task.completed_at else None,
        "user_id": task.user_id,
    }


def get_json_or_form():
    data = request.get_json(silent=True)
    if data is None and request.form:
        data = request.form.to_dict()
    return data or {}


def require_fields(data, fields):
    missing = [f for f in fields if f not in data or data[f] in (None, "")]
    if missing:
        return jsonify({"detail": f"Missing fields: {', '.join(missing)}"}), 400
    return None


@app.route("/register", methods=["POST"])
def register():
    data = get_json_or_form()
    missing_response = require_fields(data, ["username", "email", "password"])
    if missing_response:
        return missing_response

    if User.query.filter_by(username=data["username"]).first():
        return jsonify({"detail": "Username already taken"}), 400

    if User.query.filter_by(email=data["email"]).first():
        return jsonify({"detail": "Email already registered"}), 400

    hashed_password = generate_password_hash(data["password"])
    user = User(
        username=data["username"],
        email=data["email"],
        hashed_password=hashed_password,
    )
    db.session.add(user)
    db.session.commit()

    return jsonify(
        {
            "id": user.id,
            "username": user.username,
            "email": user.email,
            "is_active": user.is_active,
        }
    ), 201


@app.route("/token", methods=["POST"])
def login():
    data = get_json_or_form()
    missing_response = require_fields(data, ["username", "password"])
    if missing_response:
        return missing_response

    user = User.query.filter_by(username=data["username"]).first()
    if not user or not check_password_hash(user.hashed_password, data["password"]):
        return jsonify({"detail": "Invalid username or password"}), 401

    access_token = create_access_token(identity=str(user.id))
    return jsonify({"access_token": access_token, "token_type": "bearer"})


@app.route("/me", methods=["GET"])
@jwt_required()
def get_current_user():
    user_id = int(get_jwt_identity())
    user = User.query.get(user_id)
    if not user:
        return jsonify({"detail": "User not found"}), 404
    return jsonify(
        {
            "id": user.id,
            "username": user.username,
            "email": user.email,
            "is_active": user.is_active,
        }
    )


@app.route("/tasks", methods=["GET"])
@jwt_required()
def get_tasks():
    user_id = int(get_jwt_identity())
    completed = request.args.get("completed")

    query = Task.query.filter_by(user_id=user_id)
    if completed is not None:
        query = query.filter_by(is_completed=completed.lower() == "true")

    tasks = query.order_by(Task.created_at.desc()).all()
    return jsonify(
        [serialize_task(t) for t in tasks]
    )


@app.route("/tasks/<int:task_id>", methods=["GET"])
@jwt_required()
def get_task(task_id):
    user_id = int(get_jwt_identity())
    task = Task.query.filter_by(id=task_id, user_id=user_id).first()

    if not task:
        return jsonify({"detail": "Task not found"}), 404

    return jsonify(serialize_task(task))


@app.route("/tasks", methods=["POST"])
@jwt_required()
def create_task():
    user_id = int(get_jwt_identity())
    data = get_json_or_form()
    missing_response = require_fields(data, ["title", "latitude", "longitude"])
    if missing_response:
        return missing_response

    task = Task(
        title=data["title"],
        description=data.get("description", ""),
        latitude=float(data["latitude"]),
        longitude=float(data["longitude"]),
        address=data.get("address", ""),
        radius=int(data.get("radius", 100)),
        marker_color=int(data.get("marker_color", 0xFF2196F3)),
        marker_icon=data.get("marker_icon", "pin"),
        category=data.get("category", "general"),
        auto_remove_after_trigger=bool(data.get("auto_remove_after_trigger", False)),
        is_notification_enabled=bool(data.get("is_notification_enabled", True)),
        user_id=user_id,
    )
    db.session.add(task)
    db.session.commit()

    return jsonify(serialize_task(task)), 201


@app.route("/tasks/<int:task_id>", methods=["PUT"])
@jwt_required()
def update_task(task_id):
    user_id = int(get_jwt_identity())
    task = Task.query.filter_by(id=task_id, user_id=user_id).first()

    if not task:
        return jsonify({"detail": "Task not found"}), 404

    data = get_json_or_form()
    if "title" in data:
        task.title = data["title"]
    if "description" in data:
        task.description = data["description"]
    if "latitude" in data:
        task.latitude = float(data["latitude"])
    if "longitude" in data:
        task.longitude = float(data["longitude"])
    if "address" in data:
        task.address = data["address"]
    if "radius" in data:
        task.radius = int(data["radius"])
    if "marker_color" in data:
        task.marker_color = int(data["marker_color"])
    if "marker_icon" in data:
        task.marker_icon = data["marker_icon"]
    if "category" in data:
        task.category = data["category"]
    if "auto_remove_after_trigger" in data:
        task.auto_remove_after_trigger = bool(data["auto_remove_after_trigger"])
    if "is_completed" in data:
        is_completed = bool(data["is_completed"])
        task.is_completed = is_completed
        task.completed_at = datetime.utcnow() if is_completed else None
    if "is_notification_enabled" in data:
        task.is_notification_enabled = bool(data["is_notification_enabled"])

    db.session.commit()

    return jsonify(serialize_task(task))


@app.route("/tasks/<int:task_id>", methods=["DELETE"])
@jwt_required()
def delete_task(task_id):
    user_id = int(get_jwt_identity())
    task = Task.query.filter_by(id=task_id, user_id=user_id).first()

    if not task:
        return jsonify({"detail": "Task not found"}), 404

    db.session.delete(task)
    db.session.commit()

    return jsonify({"message": "Task deleted"})


@app.route("/tasks/<int:task_id>/toggle", methods=["POST"])
@jwt_required()
def toggle_task(task_id):
    user_id = int(get_jwt_identity())
    task = Task.query.filter_by(id=task_id, user_id=user_id).first()

    if not task:
        return jsonify({"detail": "Task not found"}), 404

    task.is_completed = not task.is_completed
    task.completed_at = datetime.utcnow() if task.is_completed else None
    db.session.commit()

    return jsonify(serialize_task(task))


@app.route("/", methods=["GET"])
def root():
    return jsonify({"message": "AOT API is running", "version": "1.0.0"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000, debug=True)
