Android offline speech-recognition app built on sherpa-onnx. Designed to maximize user privacy and provide a fast, efficient voice-input experience.

Currently the app only supports armv8a devices (for example, Qualcomm Snapdragon 810, released in early 2015) to ensure smooth input. Older phone processors are not supported.

Generally (based on subjective user experiences around the author, not a quantitative benchmark)  
- Recognition accuracy: SenseVoice > Paraformer > Zipformer  
- Device performance requirements: SenseVoice > Paraformer > Zipformer

Recommendations
- Users with MediaTek Dimensity or other non-Qualcomm chipsets: choose the generic build.  
- Users with Qualcomm Snapdragon phones: choose the QNN build for a significant performance boost (NPU inference, 2–3× faster than CPU), plus lower power consumption and lower latency.
