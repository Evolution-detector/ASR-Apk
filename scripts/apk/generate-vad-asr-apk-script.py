#!/usr/bin/env python3

import argparse
from dataclasses import dataclass
from pathlib import Path

import jinja2


def get_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--total",
        type=int,
        default=1,
        help="Number of runners",
    )
    parser.add_argument(
        "--index",
        type=int,
        default=0,
        help="Index of the current runner",
    )
    return parser.parse_args()


@dataclass
class Model:
    # We will download
    # https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/{model_name}.tar.bz2
    model_name: str

    # The type of the model, e..g, 0, 1, 2. It is hardcoded in the kotlin code
    idx: int

    # e.g., zh, en, zh_en
    lang: str
    lang2: str

    # e.g., whisper, paraformer, zipformer
    short_name: str = ""

    # cmd is used to remove extra file from the model directory
    cmd: str = ""

    rule_fsts: str = ""

    use_hr: bool = False


# See get_2nd_models() in ./generate-asr-2pass-apk-script.py
def get_models():
    models = [
        Model(
            model_name="sherpa-onnx-paraformer-zh-2023-09-14",
            idx=0,
            lang="zh_en",
            lang2="Chinese,English",
            short_name="paraformer",
            rule_fsts="itn_zh_number.fst",
            cmd="""
            if [ ! -f itn_zh_number.fst ]; then
              curl -SL -O https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/itn_zh_number.fst
            fi
            pushd $model_name

            rm -fv README.md
            rm -rfv test_wavs
            rm -fv model.onnx

            ls -lh

            popd
            """,
        ),
        Model(
            model_name="sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09",
            idx=41,
            lang="zh_en_ko_ja_yue",
            lang2="中英粤日韩",
            short_name="sense_voice_2025_09_09_int8",
            use_hr=True,
            cmd="""
            pushd $model_name

            rm -rfv test_wavs

            ls -lh

            popd
            """,
        ),
    ]
    return models


def main():
    args = get_args()
    index = args.index
    total = args.total
    assert 0 <= index < total, (index, total)

    all_model_list = get_models()

    num_models = len(all_model_list)

    num_per_runner = num_models // total
    if num_per_runner <= 0:
        raise ValueError(f"num_models: {num_models}, num_runners: {total}")

    start = index * num_per_runner
    end = start + num_per_runner

    remaining = num_models - args.total * num_per_runner

    print(f"{index}/{total}: {start}-{end}/{num_models}")

    d = dict()
    d["model_list"] = all_model_list[start:end]
    if index < remaining:
        s = args.total * num_per_runner + index
        d["model_list"].append(all_model_list[s])
        print(f"{s}/{num_models}")

    filename_list = [
        "./build-apk-vad-asr.sh",
        "./build-hap-vad-asr.sh",
        "./build-apk-vad-asr-simulate-streaming.sh",
    ]
    for filename in filename_list:
        environment = jinja2.Environment()
        if not Path(f"{filename}.in").is_file():
            print(f"skip {filename}")
            continue

        with open(f"{filename}.in") as f:
            s = f.read()
        template = environment.from_string(s)

        s = template.render(**d)
        with open(filename, "w") as f:
            print(s, file=f)


if __name__ == "__main__":
    main()
