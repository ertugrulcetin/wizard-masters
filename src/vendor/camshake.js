var Vector3;
var Quaternion;
var TransformNode;
var Matrix;
var Animation;

function initBabylon(v3, q, tn, m, anim) {
    Vector3 = v3;
    Quaternion = q;
    TransformNode = tn;
    Matrix = m;
    Animation = anim;
}

class Perlin {
    constructor() {
        this.p = new Array(256).fill(0).map(() => Math.floor(Math.random() * 256));

        for (let i = 0; i < 256; i++) {
            const r = Math.floor(Math.random() * 256);
            const t = this.p[i];
            this.p[i] = this.p[r];
            this.p[r] = t;
        }

        this.p.push(...this.p);
    }

    noise(x, y, z) {
        const X = Math.floor(x) & 255;
        const Y = Math.floor(y) & 255;
        const Z = Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);

        const u = this.fade(x);
        const v = this.fade(y);
        const w = this.fade(z);

        const A = this.p[X] + Y;
        const AA = this.p[A] + Z;
        const AB = this.p[A + 1] + Z;
        const B = this.p[X + 1] + Y;
        const BA = this.p[B] + Z;
        const BB = this.p[B + 1] + Z;

        return this.lerp(w,
            this.lerp(v,
                this.lerp(u, this.grad(this.p[AA], x, y, z),
                    this.grad(this.p[BA], x - 1, y, z)),
                this.lerp(u, this.grad(this.p[AB], x, y - 1, z),
                    this.grad(this.p[BB], x - 1, y - 1, z))),
            this.lerp(v,
                this.lerp(u, this.grad(this.p[AA + 1], x, y, z - 1),
                    this.grad(this.p[BA + 1], x - 1, y, z - 1)),
                this.lerp(u, this.grad(this.p[AB + 1], x, y - 1, z - 1),
                    this.grad(this.p[BB + 1], x - 1, y - 1, z - 1))));
    }

    noise1D(x) {
        return this.noise(x, x, x);
    }

    fade(t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    lerp(t, a, b) {
        return a + t * (b - a);
    }

    grad(hash, x, y, z) {
        const h = hash & 15;
        const u = h < 8 ? x : y;
        const v = h < 4 ? y : h === 12 || h === 14 ? x : z;
        return ((h & 1) === 0 ? u : -u) + ((h & 2) === 0 ? v : -v);
    }
}

const ShakePatternFn = (x) => Math.sin(x); // Default shake pattern function

class CamShake {
    constructor() {
        this.speed = 2;
        this.shakePattern = 'perlin';
        this.perlin = new Perlin();

        this.amplitude = new Vector3(1);
        this.frequency = new Vector3(3, 9, 0);
        this.frequencyOffset = Math.random();

        this.rotation = new Vector3(0.01, 0, 0);
        this.rotationFrequency = new Vector3(0, 1, 0);
        this.rotationOffset = Math.random();

        this.influence = 2;
        this.shakeParent = new TransformNode('shakeParent');
    }

    shake(target) {
        const speed = this.speed;

        const shakePatternFunc = this.shakePattern === 'perlin' ? this.perlin.noise1D.bind(this.perlin) : ShakePatternFn;

        const time = Date.now() / 1000 * speed;

        // this.shakeParent.position = target.position.clone();
        this.shakeParent.position = new Vector3(0, 0, 0);
        this.shakeParent.rotation = new Vector3(0, 0, 0);
        target.parent = this.shakeParent;

        this.shakePosition(time, target, shakePatternFunc);
        this.shakeRotation(time, target, shakePatternFunc);
    }

    shakePosition(time, target, shakePatternFunc) {
        const amplitude = this.getValueAsVector(this.amplitude);
        if (amplitude.length() === 0) {
            return;
        } // No need to calculate if amplitude is zero

        const frequency = this.getValueAsVector(this.frequency);
        const frequencyOffset = this.frequencyOffset;
        const influence = this.influence;

        const x = amplitude.x * (shakePatternFunc((frequency.x + frequencyOffset) * time) * 0.5 + 0.5) * influence;
        const y = amplitude.y * (shakePatternFunc((frequency.y + frequencyOffset) * time) * 0.5 + 0.5) * influence;
        const z = amplitude.z * (shakePatternFunc((frequency.z + frequencyOffset) * time) * 0.5 + 0.5) * influence;

        const left = target.getDirection(Vector3.Left());
        const up = target.getDirection(Vector3.Up());
        const forward = target.getDirection(Vector3.Forward());

        const offsetX = left.scale(x - amplitude.x * 0.5);
        const offsetY = up.scale(y - amplitude.y * 0.5);
        const offsetZ = forward.scale(z - amplitude.z * 0.5);

        const offset = offsetX.add(offsetY).add(offsetZ);
        target.parent.position = target.parent.position.add(offset);
    }

    shakeRotation(time, target, shakePatternFunc) {
        const rotation = this.getValueAsVector(this.rotation);
        if (rotation.length() === 0) {
            return;
        } // No need to calculate if rotation is zero

        const frequency = this.getValueAsVector(this.frequency);
        const rotationFrequency = this.getValueAsVector(this.rotationFrequency);
        const rotationOffset = this.rotationOffset;
        const influence = this.influence;

        const x = rotation.x * shakePatternFunc((frequency.x + rotationFrequency.x + rotationOffset) * time) * influence;
        const y = rotation.y * shakePatternFunc((frequency.y + rotationFrequency.y + rotationOffset) * time) * influence;
        const z = rotation.z * shakePatternFunc((frequency.z + rotationFrequency.z + rotationOffset) * time) * influence;

        const left = target.getDirection(Vector3.Left());
        const up = target.getDirection(Vector3.Up()); // typo fixed here
        const forward = target.getDirection(Vector3.Forward());

        const quaternionX = Quaternion.RotationAxis(left, x);
        const quaternionY = Quaternion.RotationAxis(up, y);
        const quaternionZ = Quaternion.RotationAxis(forward, z);

        const finalQuaternion = quaternionX.multiply(quaternionY).multiply(quaternionZ);

        target.parent.rotation = finalQuaternion.toEulerAngles();
    }

    getValueAsVector(value) {
        if (!value) {
            return Vector3.Zero();
        }

        return value instanceof Vector3 ? value : new Vector3(value, value, value);
    }
}

export {CamShake, initBabylon};
