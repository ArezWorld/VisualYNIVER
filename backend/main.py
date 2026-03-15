from datetime import datetime, timedelta
import os
import re
import secrets

from flask import Flask, jsonify, request
from flask_cors import CORS
from flask_jwt_extended import JWTManager, create_access_token, get_jwt_identity, jwt_required
from flask_sqlalchemy import SQLAlchemy
from sqlalchemy import inspect, text
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token
from dotenv import load_dotenv
from werkzeug.security import check_password_hash, generate_password_hash

load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

app = Flask(__name__)


def resolve_database_url():
    raw_url = os.getenv("DATABASE_URL", "sqlite:///aot.db").strip()
    if raw_url.startswith("postgres://"):
        raw_url = raw_url.replace("postgres://", "postgresql://", 1)
    if raw_url.startswith("postgresql://"):
        return raw_url.replace("postgresql://", "postgresql+psycopg://", 1)
    return raw_url


app.config["SECRET_KEY"] = os.getenv("SECRET_KEY", "aot_secret_key_change_in_production")
app.config["JWT_SECRET_KEY"] = os.getenv("JWT_SECRET_KEY", "aot_jwt_secret_key_change_in_production")
app.config["SQLALCHEMY_DATABASE_URI"] = resolve_database_url()
app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False
app.config["SQLALCHEMY_ENGINE_OPTIONS"] = {
    "pool_pre_ping": True,
}
app.config["JWT_ACCESS_TOKEN_EXPIRES"] = timedelta(days=7)
app.config["GOOGLE_WEB_CLIENT_ID"] = os.getenv("GOOGLE_WEB_CLIENT_ID", "").strip()

db = SQLAlchemy(app)
cors_origins = os.getenv("CORS_ORIGINS", "*")
if cors_origins == "*":
    CORS(app)
else:
    origins = [item.strip() for item in cors_origins.split(",") if item.strip()]
    CORS(app, resources={r"/*": {"origins": origins}})
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
    inspector = inspect(db.engine)
    if "task" not in inspector.get_table_names():
        return

    existing_columns = {column["name"] for column in inspector.get_columns("task")}
    dialect = db.engine.dialect.name
    statements = {
        "marker_color": "ALTER TABLE task ADD COLUMN marker_color INTEGER DEFAULT 4280391411",
        "marker_icon": "ALTER TABLE task ADD COLUMN marker_icon VARCHAR(64) DEFAULT 'pin'",
        "category": "ALTER TABLE task ADD COLUMN category VARCHAR(64) DEFAULT 'general'",
        "auto_remove_after_trigger": "ALTER TABLE task ADD COLUMN auto_remove_after_trigger BOOLEAN DEFAULT FALSE",
    }

    for column, query in statements.items():
        if column in existing_columns:
            continue
        # Для SQLite значение FALSE не всегда доступно как литерал.
        if dialect == "sqlite" and column == "auto_remove_after_trigger":
            query = "ALTER TABLE task ADD COLUMN auto_remove_after_trigger BOOLEAN DEFAULT 0"
        db.session.execute(text(query))
        db.session.commit()


with app.app_context():
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


def parse_bool(value, default=False):
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return value != 0
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"true", "1", "yes", "y", "on"}:
            return True
        if normalized in {"false", "0", "no", "n", "off"}:
            return False
    return default


def sanitize_username(raw_value):
    value = (raw_value or "").strip().lower()
    value = re.sub(r"[^a-z0-9_]+", "_", value)
    value = value.strip("_")
    if len(value) < 3:
        value = f"user_{secrets.token_hex(2)}"
    return value[:40]


def make_unique_username(base_username):
    candidate = sanitize_username(base_username)
    suffix = 1
    while User.query.filter_by(username=candidate).first():
        suffix += 1
        candidate = f"{base_username}_{suffix}"
        candidate = sanitize_username(candidate)
    return candidate


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


@app.route("/auth/google", methods=["POST"])
def login_with_google():
    data = get_json_or_form()
    missing_response = require_fields(data, ["id_token"])
    if missing_response:
        return missing_response

    client_id = app.config.get("GOOGLE_WEB_CLIENT_ID", "")
    if not client_id:
        return jsonify({"detail": "Google login is not configured on server"}), 503

    try:
        payload = google_id_token.verify_oauth2_token(
            data["id_token"],
            google_requests.Request(),
            client_id,
        )
    except Exception:
        return jsonify({"detail": "Invalid Google token"}), 401

    email = (payload.get("email") or "").strip().lower()
    if not email:
        return jsonify({"detail": "Google account email is missing"}), 400
    if not payload.get("email_verified", False):
        return jsonify({"detail": "Google email is not verified"}), 400

    user = User.query.filter_by(email=email).first()
    if not user:
        name_candidate = payload.get("name") or payload.get("given_name") or email.split("@")[0]
        username = make_unique_username(name_candidate)
        user = User(
            username=username,
            email=email,
            hashed_password=generate_password_hash(secrets.token_urlsafe(32)),
            is_active=True,
        )
        db.session.add(user)
        db.session.commit()

    access_token = create_access_token(identity=str(user.id))
    return jsonify({"access_token": access_token, "token_type": "bearer"})


@app.route("/auth/config", methods=["GET"])
def auth_config():
    client_id = app.config.get("GOOGLE_WEB_CLIENT_ID", "").strip()
    return jsonify(
        {
            "google_sign_in_enabled": bool(client_id),
            "google_web_client_id": client_id,
        }
    )


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
        auto_remove_after_trigger=parse_bool(data.get("auto_remove_after_trigger"), False),
        is_notification_enabled=parse_bool(data.get("is_notification_enabled"), True),
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
        task.auto_remove_after_trigger = parse_bool(data["auto_remove_after_trigger"], False)
    if "is_completed" in data:
        is_completed = parse_bool(data["is_completed"], False)
        task.is_completed = is_completed
        task.completed_at = datetime.utcnow() if is_completed else None
    if "is_notification_enabled" in data:
        task.is_notification_enabled = parse_bool(data["is_notification_enabled"], True)

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


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"}), 200


if __name__ == "__main__":
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8000"))
    debug_enabled = os.getenv("FLASK_DEBUG", "false").strip().lower() == "true"
    app.run(host=host, port=port, debug=debug_enabled)
