uniform float g_Time;

varying vec4 worldPos;

// scrolling tiles
void main(){
    float offset = mod(g_Time, 2);
    float brightness = mod(int(worldPos.x + offset) + int(worldPos.z), 2);// 0 or 1
    float colorValue = 0.5 * (brightness + 1);
    gl_FragColor = vec4(0.0, 0.0, colorValue, 0.9);
}
