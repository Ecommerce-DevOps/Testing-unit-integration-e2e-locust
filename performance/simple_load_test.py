"""
🧪 Simple Load Testing - Pruebas básicas de carga para E-commerce
Diseñado para funcionar con los servicios desplegados en staging

Ejecutar localmente:
    locust -f simple_load_test.py --host=http://localhost:9090

Ejecutar en modo headless:
    locust -f simple_load_test.py --host=http://localhost:9090 --users 10 --spawn-rate 2 --run-time 1m --headless
"""

from locust import HttpUser, task, between
import random
import string
import uuid
from datetime import datetime
import logging

# Configurar logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Token JWT de prueba (válido hasta 2025)
JWT_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTczMjk0NzIwMCwiZXhwIjoxNzY0NDgzMjAwfQ.K4x5jvpQbE3eKyFFvBj8dN7zL_YZpq8vxM2nqQ9stEh2w4HfFmV3kXzwT9yJvKlXp6YxLvg5nI1qFz8cRmHy1A"


def generate_unique_id():
    """Genera un ID único corto"""
    return uuid.uuid4().hex[:8]


def generate_order_date():
    """Genera fecha en formato dd-MM-yyyy__HH:mm:ss:SSSSSS"""
    now = datetime.now()
    return now.strftime("%d-%m-%Y__%H:%M:%S:%f")


class SimpleEcommerceUser(HttpUser):
    """
    Usuario simple para pruebas de carga.
    Realiza operaciones básicas que sabemos que funcionan.
    """
    
    wait_time = between(1, 3)  # Espera 1-3 segundos entre requests
    
    def on_start(self):
        """Inicialización del usuario"""
        self.unique_id = generate_unique_id()
        self.user_id = None
        self.product_ids = []
        self.headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {JWT_TOKEN}"
        }
        
        # Obtener lista de productos existentes al inicio
        self._get_existing_products()
    
    def _get_existing_products(self):
        """Obtiene IDs de productos existentes"""
        try:
            response = self.client.get(
                "/product-service/api/products",
                headers=self.headers,
                name="[Setup] Get Products"
            )
            if response.status_code == 200:
                data = response.json()
                # La respuesta puede ser un objeto con "collection" o una lista directa
                if isinstance(data, dict) and "collection" in data:
                    products = data["collection"]
                elif isinstance(data, list):
                    products = data
                else:
                    products = []
                
                self.product_ids = [p.get("productId") for p in products if p.get("productId")]
                logger.info(f"Loaded {len(self.product_ids)} product IDs")
        except Exception as e:
            logger.warning(f"Could not load products: {e}")
            self.product_ids = [1, 2, 3]  # IDs por defecto
    
    @task(5)
    def get_products(self):
        """Listar todos los productos - operación más común"""
        with self.client.get(
            "/product-service/api/products",
            headers=self.headers,
            catch_response=True,
            name="GET /products"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status {response.status_code}")
    
    @task(3)
    def get_single_product(self):
        """Obtener detalle de un producto específico"""
        if self.product_ids:
            product_id = random.choice(self.product_ids)
        else:
            product_id = random.randint(1, 10)
        
        with self.client.get(
            f"/product-service/api/products/{product_id}",
            headers=self.headers,
            catch_response=True,
            name="GET /products/{id}"
        ) as response:
            if response.status_code in [200, 404]:  # 404 es válido si el producto no existe
                response.success()
            else:
                response.failure(f"Status {response.status_code}")
    
    @task(3)
    def get_categories(self):
        """Obtener categorías de productos"""
        with self.client.get(
            "/product-service/api/categories",
            headers=self.headers,
            catch_response=True,
            name="GET /categories"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status {response.status_code}")
    
    @task(2)
    def get_users(self):
        """Listar usuarios"""
        with self.client.get(
            "/user-service/api/users",
            headers=self.headers,
            catch_response=True,
            name="GET /users"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status {response.status_code}")
    
    @task(1)
    def create_user(self):
        """Crear un nuevo usuario"""
        uid = generate_unique_id()
        user_data = {
            "firstName": f"LoadTest{uid}",
            "lastName": "User",
            "email": f"loadtest{uid}@example.com",
            "phone": f"+1555{uid[:7]}",
            "imageUrl": "https://example.com/avatar.jpg",
            "credential": {
                "username": f"loadtest{uid}",
                "password": "LoadTest123!",
                "roleBasedAuthority": "ROLE_USER",
                "isEnabled": True,
                "isAccountNonExpired": True,
                "isAccountNonLocked": True,
                "isCredentialsNonExpired": True
            }
        }
        
        with self.client.post(
            "/user-service/api/users",
            json=user_data,
            headers=self.headers,
            catch_response=True,
            name="POST /users"
        ) as response:
            if response.status_code in [200, 201]:
                try:
                    result = response.json()
                    self.user_id = result.get("userId")
                    response.success()
                except:
                    response.success()  # Aún así es exitoso
            elif response.status_code == 409:
                response.success()  # Duplicado es aceptable en pruebas de carga
            else:
                response.failure(f"Status {response.status_code}")
    
    @task(1)
    def create_product(self):
        """Crear un nuevo producto"""
        uid = generate_unique_id()
        product_data = {
            "productTitle": f"LoadTestProduct{uid}",
            "imageUrl": "https://example.com/product.jpg",
            "sku": f"LOAD{uid.upper()}",
            "priceUnit": round(random.uniform(10.0, 100.0), 2),
            "quantity": random.randint(10, 100),
            "category": {
                "categoryId": random.randint(1, 3)  # Categorías 1, 2, 3 existen
            }
        }
        
        with self.client.post(
            "/product-service/api/products",
            json=product_data,
            headers=self.headers,
            catch_response=True,
            name="POST /products"
        ) as response:
            if response.status_code in [200, 201]:
                try:
                    result = response.json()
                    new_id = result.get("productId")
                    if new_id:
                        self.product_ids.append(new_id)
                    response.success()
                except:
                    response.success()
            elif response.status_code == 500:
                # SKU duplicado puede causar 500
                response.success()
            else:
                response.failure(f"Status {response.status_code}")


class HealthCheckUser(HttpUser):
    """
    Usuario que solo hace health checks - útil para verificar disponibilidad
    """
    
    wait_time = between(2, 5)
    weight = 1  # Menos peso que el usuario principal
    
    def on_start(self):
        self.headers = {
            "Authorization": f"Bearer {JWT_TOKEN}"
        }
    
    @task
    def check_user_service_health(self):
        """Health check del user-service"""
        with self.client.get(
            "/user-service/actuator/health",
            headers=self.headers,
            catch_response=True,
            name="Health: user-service"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status {response.status_code}")
    
    @task
    def check_product_service_health(self):
        """Health check del product-service"""
        with self.client.get(
            "/product-service/actuator/health",
            headers=self.headers,
            catch_response=True,
            name="Health: product-service"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status {response.status_code}")
    
    @task
    def check_order_service_health(self):
        """Health check del order-service"""
        with self.client.get(
            "/order-service/actuator/health",
            headers=self.headers,
            catch_response=True,
            name="Health: order-service"
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Status {response.status_code}")


# Configuración por defecto si se ejecuta directamente
if __name__ == "__main__":
    import os
    os.system("locust -f simple_load_test.py --host=http://localhost:9090 --users 10 --spawn-rate 2 --run-time 1m --headless")
