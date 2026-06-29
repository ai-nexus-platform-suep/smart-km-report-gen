"""RabbitMQ consumer for document processing tasks."""
import json
import threading
import uuid
import logging
import httpx
import pika
from app.config import settings
from app.parser import DocumentParser
from app.chunker import create_chunker
from app.embedder import Embedder
from app.milvus_client import MilvusClient
from app.minio_client import MinioReader

logger = logging.getLogger(__name__)


class DocumentConsumer:
    """Consumes document processing tasks from RabbitMQ."""

    def __init__(self):
        self.parser = DocumentParser()
        self.embedder = Embedder()
        self.milvus = MilvusClient()
        self.minio = MinioReader()
        self._running = False
        self._thread = None

    def start(self):
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()
        logger.info("DocumentConsumer started")

    def stop(self):
        self._running = False

    def _run(self):
        while self._running:
            try:
                params = pika.ConnectionParameters(
                    host=settings.rabbitmq_host,
                    port=settings.rabbitmq_port,
                    credentials=pika.PlainCredentials(settings.rabbitmq_user, settings.rabbitmq_password),
                    heartbeat=600,
                )
                connection = pika.BlockingConnection(params)
                channel = connection.channel()
                channel.queue_declare(queue=settings.rabbitmq_queue, durable=True)
                channel.basic_qos(prefetch_count=settings.max_concurrent_tasks)
                channel.basic_consume(queue=settings.rabbitmq_queue, on_message_callback=self._callback)
                logger.info("RabbitMQ connected, waiting for messages...")
                channel.start_consuming()
            except Exception as e:
                logger.error("RabbitMQ error: %s, retrying in 5s...", e)
                time.sleep(5)

    def _callback(self, channel, method, properties, body):
        try:
            msg = json.loads(body)
            logger.info("Processing document: %s", msg.get("documentId"))
            self._process_document(msg)
            channel.basic_ack(delivery_tag=method.delivery_tag)
        except Exception as e:
            logger.error("Failed to process message: %s", e)
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

    def _process_document(self, msg):
        doc_id = msg["documentId"]
        kb_id = msg["kbId"]
        minio_path = msg["minioPath"]
        mime_type = msg.get("mimeType", "application/octet-stream")
        strategy = msg.get("chunkStrategy", {"type": "heading"})
        callback_url = msg.get("callbackUrl", "")

        try:
            # 1. Callback: PARSING
            self._callback_status(doc_id, "PARSING", callback_url)

            # 2. Read file from MinIO
            file_content = self.minio.read_file(minio_path)

            # 3. Parse document to text
            text = self.parser.parse(file_content, mime_type, minio_path)
            if not text.strip():
                raise ValueError("Empty document after parsing")

            # 4. Callback: CHUNKING
            self._callback_status(doc_id, "CHUNKING", callback_url)

            # 5. Chunk by strategy
            chunker = create_chunker(strategy)
            chunk_results = chunker.chunk(text)

            if not chunk_results:
                raise ValueError("No chunks produced")

            # 6. Callback: EMBEDDING
            self._callback_status(doc_id, "EMBEDDING", callback_url)

            # 7. Embed chunks
            texts = [c.content for c in chunk_results]
            vectors = self.embedder.embed(texts)

            # 8. Write to Milvus
            milvus_records = []
            chunk_records = []
            for i, (cr, vec) in enumerate(zip(chunk_results, vectors)):
                chunk_uuid = str(uuid.uuid4())
                vector_uuid = str(uuid.uuid4())
                milvus_records.append({
                    "id": vector_uuid,
                    "chunk_id": chunk_uuid,
                    "doc_id": doc_id,
                    "kb_id": kb_id,
                    "embedding": vec,
                    "content": cr.content[:60000] if len(cr.content) > 60000 else cr.content,
                    "chapter_path": cr.chapter_path[:500] if len(cr.chapter_path) > 500 else cr.chapter_path,
                })
                chunk_records.append({
                    "id": chunk_uuid,
                    "doc_id": doc_id,
                    "content": cr.content,
                    "chapter_path": cr.chapter_path,
                    "chunk_index": i,
                    "chunk_type": cr.chunk_type,
                    "char_count": len(cr.content),
                    "vector_id": vector_uuid,
                })
            self.milvus.insert(milvus_records)

            # 9. Callback: ready with chunks
            self._callback_ready(doc_id, chunk_records, callback_url)
            logger.info("Document %s processed: %d chunks", doc_id, len(chunk_records))

        except Exception as e:
            logger.error("Document %s processing failed: %s", doc_id, str(e))
            self._callback_status(doc_id, "FAILED", callback_url, error_msg=str(e))

    def _callback_status(self, doc_id, status, callback_url, error_msg=""):
        if callback_url:
            try:
                body = {"documentId": doc_id, "status": status, "errorMsg": error_msg, "chunks": []}
                httpx.post(callback_url, json=body, timeout=5.0)
            except Exception:
                logger.warning("Status callback failed for %s -> %s", doc_id, status)

    def _callback_ready(self, doc_id, chunks, callback_url):
        if callback_url:
            try:
                body = {"documentId": doc_id, "status": "READY", "errorMsg": "", "chunks": chunks}
                httpx.post(callback_url, json=body, timeout=10.0)
            except Exception as e:
                logger.warning("Ready callback failed for %s: %s", doc_id, e)
