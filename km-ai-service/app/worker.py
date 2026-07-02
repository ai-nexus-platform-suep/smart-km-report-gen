from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import logging
from pathlib import Path
import sys

import pika

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from app.processor import DocumentProcessJob, DocumentProcessor
    from app.settings import Settings
else:
    from .processor import DocumentProcessJob, DocumentProcessor
    from .settings import Settings


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger(__name__)


def run_worker() -> None:
    settings = Settings.from_env()
    credentials = pika.PlainCredentials(settings.rabbitmq_username, settings.rabbitmq_password)
    parameters = pika.ConnectionParameters(
        host=settings.rabbitmq_host,
        port=settings.rabbitmq_port,
        virtual_host=settings.rabbitmq_virtual_host,
        credentials=credentials,
    )
    connection = pika.BlockingConnection(parameters)
    channel = connection.channel()
    channel.exchange_declare(exchange=settings.rabbitmq_exchange, exchange_type="direct", durable=True)
    channel.exchange_declare(exchange=settings.rabbitmq_dead_letter_exchange, exchange_type="direct", durable=True)
    channel.queue_declare(
        queue=settings.rabbitmq_queue,
        durable=True,
        arguments={
            "x-dead-letter-exchange": settings.rabbitmq_dead_letter_exchange,
            "x-dead-letter-routing-key": settings.rabbitmq_dead_letter_routing_key,
        },
    )
    channel.queue_declare(queue=settings.rabbitmq_dead_letter_queue, durable=True)
    channel.queue_bind(
        queue=settings.rabbitmq_queue,
        exchange=settings.rabbitmq_exchange,
        routing_key=settings.rabbitmq_routing_key,
    )
    channel.queue_bind(
        queue=settings.rabbitmq_dead_letter_queue,
        exchange=settings.rabbitmq_dead_letter_exchange,
        routing_key=settings.rabbitmq_dead_letter_routing_key,
    )
    channel.basic_qos(prefetch_count=settings.rabbitmq_prefetch_count)

    executor = ThreadPoolExecutor(max_workers=settings.max_concurrent_parse_jobs)

    def on_message(channel, method, properties, body: bytes) -> None:
        delivery_tag = method.delivery_tag

        def process_delivery() -> None:
            try:
                payload = json.loads(body.decode("utf-8"))
                job = DocumentProcessJob.from_message(payload)
                processor = DocumentProcessor(settings)
                processor.process(job)
                connection.add_callback_threadsafe(lambda: channel.basic_ack(delivery_tag=delivery_tag))
            except Exception:
                logger.exception("Document processing message failed")
                connection.add_callback_threadsafe(
                    lambda: channel.basic_nack(delivery_tag=delivery_tag, requeue=False)
                )

        executor.submit(process_delivery)

    logger.info(
        "Document worker started, queue=%s, prefetch=%s, max_concurrent=%s",
        settings.rabbitmq_queue,
        settings.rabbitmq_prefetch_count,
        settings.max_concurrent_parse_jobs,
    )
    channel.basic_consume(queue=settings.rabbitmq_queue, on_message_callback=on_message)
    try:
        channel.start_consuming()
    finally:
        executor.shutdown(wait=True)
        if connection.is_open:
            try:
                connection.close()
            except pika.exceptions.ConnectionWrongStateError:
                logger.debug("RabbitMQ connection already closed during worker shutdown")


def main() -> None:
    parser = argparse.ArgumentParser(description="Run km-ai-service document processing worker")
    parser.parse_args()
    run_worker()


if __name__ == "__main__":
    main()
