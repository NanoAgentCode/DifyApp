#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
EasyOCR服务配置读取。
"""

import configparser
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Set


BASE_DIR = Path(__file__).resolve().parent
DEFAULT_CONFIG_PATH = BASE_DIR / "config.ini"


@dataclass(frozen=True)
class ServerConfig:
    host: str
    port: int
    debug: bool
    cors_origins: list[str]


@dataclass(frozen=True)
class OcrConfig:
    languages: list[str]
    gpu: bool
    max_content_length: int
    max_pdf_pages: int
    pdf_dpi: int
    max_batch_size: int
    max_image_pixels: int
    supported_image_extensions: Set[str]


@dataclass(frozen=True)
class AppConfig:
    server: ServerConfig
    ocr: OcrConfig


def _load_parser() -> configparser.ConfigParser:
    config_path = Path(os.environ.get("EASY_OCR_CONFIG", DEFAULT_CONFIG_PATH))
    parser = configparser.ConfigParser()
    parser.read(config_path, encoding="utf-8")
    return parser


def _get(parser: configparser.ConfigParser, section: str, option: str, default: str, env_name: str) -> str:
    return os.environ.get(env_name) or parser.get(section, option, fallback=default)


def _get_int(parser: configparser.ConfigParser, section: str, option: str, default: int, env_name: str) -> int:
    raw_value = _get(parser, section, option, str(default), env_name)
    return int(raw_value)


def _get_bool(parser: configparser.ConfigParser, section: str, option: str, default: bool, env_name: str) -> bool:
    raw_value = _get(parser, section, option, str(default).lower(), env_name).strip().lower()
    return raw_value in {"1", "true", "yes", "on"}


def _get_csv(parser: configparser.ConfigParser, section: str, option: str, default: str, env_name: str) -> list[str]:
    raw_value = _get(parser, section, option, default, env_name)
    return [item.strip() for item in raw_value.split(",") if item.strip()]


def _get_extensions(parser: configparser.ConfigParser) -> Set[str]:
    values = _get_csv(
        parser,
        "ocr",
        "supported_image_extensions",
        ".png,.jpg,.jpeg,.bmp,.webp,.tif,.tiff",
        "SUPPORTED_IMAGE_EXTENSIONS",
    )
    return {value if value.startswith(".") else f".{value}" for value in values}


def load_config() -> AppConfig:
    parser = _load_parser()

    server = ServerConfig(
        host=_get(parser, "server", "host", "0.0.0.0", "HOST"),
        port=_get_int(parser, "server", "port", 8000, "PORT"),
        debug=_get_bool(parser, "server", "debug", False, "DEBUG"),
        cors_origins=_get_csv(parser, "server", "cors_origins", "*", "CORS_ORIGINS"),
    )

    ocr = OcrConfig(
        languages=_get_csv(parser, "ocr", "languages", "ch_sim,en", "OCR_LANGUAGES"),
        gpu=_get_bool(parser, "ocr", "gpu", False, "OCR_GPU"),
        max_content_length=_get_int(parser, "ocr", "max_content_length", 50 * 1024 * 1024, "MAX_CONTENT_LENGTH"),
        max_pdf_pages=_get_int(parser, "ocr", "max_pdf_pages", 10, "MAX_PDF_PAGES"),
        pdf_dpi=_get_int(parser, "ocr", "pdf_dpi", 200, "PDF_DPI"),
        max_batch_size=_get_int(parser, "ocr", "max_batch_size", 10, "MAX_BATCH_SIZE"),
        max_image_pixels=_get_int(parser, "ocr", "max_image_pixels", 25_000_000, "MAX_IMAGE_PIXELS"),
        supported_image_extensions=_get_extensions(parser),
    )

    return AppConfig(server=server, ocr=ocr)
