# Book Inventory Web App

This is a minimal Flask-based web application for managing books in libraries and tracking sales.

## Setup

1. Create a virtual environment and install dependencies:

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

Optionally set environment variables to customize the database location and secret key:

```bash
export DATABASE_URL=sqlite:///my_books.db
export SECRET_KEY=change-me
```

2. Run the application:

```bash
python app.py
```

The app will start on `http://127.0.0.1:5000/`.

## Web Interface

Visit `http://127.0.0.1:5000/` in your browser to use a simple web interface.
You can add libraries and books, view inventory, and record sales from the page.

## API Endpoints

- `POST /libraries` – Create a library. JSON body: `{"name": "Main Library"}`
- `GET /libraries` – List libraries.
- `POST /books` – Add a book. JSON body: `{"title": "Title", "author": "Author", "library_id": 1, "quantity": 10}`
- `GET /books` – List books.
- `POST /sell` – Record a sale. JSON body: `{"book_id": 1, "quantity": 2}`
- `GET /sales` – List all recorded sales.
