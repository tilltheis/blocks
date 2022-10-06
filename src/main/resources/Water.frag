uniform float g_Time;

varying vec4 worldPos;

const float PI = 3.1415926535897932384626433832795;

float easeInOutSine(float x) {
    return -(cos(PI * x) - 1) / 2;
}

// scrolling tiles
void main(){
    float offset = mod(g_Time/3, 2);
    // floor because both (int)-0.9f and (int)+0.9f result in 0, effectively spanning 2 ints
    float brightness = mod(floor(worldPos.x + easeInOutSine(offset)) + floor(worldPos.z), 2);// 0 or 1
    float colorValue = 0.5 * (brightness + 1);
    gl_FragColor = vec4(0.0, 0.0, colorValue, 0.9);
}
