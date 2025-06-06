import os
from flask import Flask, request, jsonify, render_template, redirect, url_for
from flask_sqlalchemy import SQLAlchemy
from datetime import datetime

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = os.environ.get('DATABASE_URL', 'sqlite:///books.db')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY', 'book-inventory-secret')

db = SQLAlchemy(app)

class Library(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(120), nullable=False, unique=True)

class Book(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    title = db.Column(db.String(200), nullable=False)
    author = db.Column(db.String(200), nullable=False)
    library_id = db.Column(db.Integer, db.ForeignKey('library.id'), nullable=False)
    quantity = db.Column(db.Integer, default=0)

class Sale(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    book_id = db.Column(db.Integer, db.ForeignKey('book.id'), nullable=False)
    quantity = db.Column(db.Integer, nullable=False)
    sale_date = db.Column(db.DateTime, default=datetime.utcnow)


@app.route('/libraries', methods=['GET', 'POST'])
def libraries():
    if request.method == 'POST':
        name = request.json.get('name')
        if not name:
            return jsonify({'error': 'name is required'}), 400
        if Library.query.filter_by(name=name).first():
            return jsonify({'error': 'library already exists'}), 400
        library = Library(name=name)
        db.session.add(library)
        db.session.commit()
        return jsonify({'id': library.id, 'name': library.name}), 201
    else:
        libraries = Library.query.all()
        return jsonify([{'id': l.id, 'name': l.name} for l in libraries])

@app.route('/books', methods=['GET', 'POST'])
def books():
    if request.method == 'POST':
        data = request.json
        title = data.get('title')
        author = data.get('author')
        library_id = data.get('library_id')
        quantity = data.get('quantity', 0)
        if not (title and author and library_id is not None):
            return jsonify({'error': 'title, author and library_id are required'}), 400
        library = Library.query.get(library_id)
        if not library:
            return jsonify({'error': 'invalid library_id'}), 400
        book = Book.query.filter_by(title=title, author=author, library_id=library_id).first()
        if book:
            book.quantity += int(quantity)
        else:
            book = Book(title=title, author=author, library_id=library_id, quantity=quantity)
            db.session.add(book)
        db.session.commit()
        return jsonify({'id': book.id, 'quantity': book.quantity}), 201
    else:
        books = Book.query.all()
        return jsonify([{'id': b.id, 'title': b.title, 'author': b.author, 'library_id': b.library_id, 'quantity': b.quantity} for b in books])

@app.route('/sell', methods=['POST'])
def sell_book():
    data = request.json
    book_id = data.get('book_id')
    quantity = data.get('quantity', 1)
    book = Book.query.get_or_404(book_id)
    if quantity <= 0:
        return jsonify({'error': 'quantity must be positive'}), 400
    if book.quantity < quantity:
        return jsonify({'error': 'insufficient stock'}), 400
    book.quantity -= quantity
    sale = Sale(book_id=book_id, quantity=quantity)
    db.session.add(sale)
    db.session.commit()
    return jsonify({'message': 'sale recorded'})

@app.route('/sales', methods=['GET'])
def sales():
    sales = Sale.query.all()
    return jsonify([{'id': s.id, 'book_id': s.book_id, 'quantity': s.quantity, 'sale_date': s.sale_date.isoformat()} for s in sales])


# -----------------------
# Web frontend routes
# -----------------------

@app.route('/')
def index():
    libraries = Library.query.all()
    books = Book.query.all()
    sales = Sale.query.all()
    return render_template('index.html', libraries=libraries, books=books, sales=sales)


@app.route('/add_library', methods=['POST'])
def add_library():
    name = request.form.get('name')
    if name and not Library.query.filter_by(name=name).first():
        library = Library(name=name)
        db.session.add(library)
        db.session.commit()
    return redirect(url_for('index'))


@app.route('/add_book', methods=['POST'])
def add_book():
    title = request.form.get('title')
    author = request.form.get('author')
    library_id = request.form.get('library_id')
    quantity = request.form.get('quantity', 0)
    if title and author and library_id:
        library = Library.query.get(int(library_id))
        if library:
            book = Book.query.filter_by(title=title, author=author, library_id=library.id).first()
            if book:
                book.quantity += int(quantity)
            else:
                book = Book(title=title, author=author, library_id=library.id, quantity=int(quantity))
                db.session.add(book)
            db.session.commit()
    return redirect(url_for('index'))


@app.route('/sell_form', methods=['POST'])
def sell_form():
    book_id = request.form.get('book_id')
    quantity = int(request.form.get('quantity', 1))
    book = Book.query.get_or_404(book_id)
    if quantity > 0 and book.quantity >= quantity:
        book.quantity -= quantity
        sale = Sale(book_id=book.id, quantity=quantity)
        db.session.add(sale)
        db.session.commit()
    return redirect(url_for('index'))

if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True)
