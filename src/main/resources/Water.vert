uniform mat4 g_WorldViewProjectionMatrix;
uniform mat4 g_WorldMatrix;

attribute vec3 inPosition;

varying vec4 worldPos;

void main(){
    // set for fragment shader
    worldPos = g_WorldMatrix * vec4(inPosition, 1.0);

    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
